# フェーズX-8：深夜帯メンテナンスウィンドウとバッチ実行中の業務規制

## ステータス
🔲 未着手（設計のみ・2026-05-08 作成）

## 改訂履歴

| 版 | 日付 | 主な内容 |
|----|------|---------|
| 初版 | 2026-05-08 | phase17 Step 6 実装中に「t3.micro でバッチ実行中の応答遅延」が顕在化したため新設 |

## 位置付け
時系列フェーズ（1〜20）に依存しない横断的運用品質フェーズ。

phaseX-4 で t3.micro 復帰のため `-Xmx384m + Swap 2GB` のメモリチューニングを完了させたが、
**バッチ実行中の同時負荷（CPU・ディスク I/O・JDBC コネクション競合）には未対応**だった。
phase17 でバッチ処理基盤が拡充され、`PostalCsvImportJob`（KEN_ALL.CSV 12 万件 INSERT）等の
重量バッチが追加された結果、**深夜帯にバッチが起動するとユーザの API 応答が極端に遅くなる**
リスクが顕在化した。

本フェーズは phaseX-4（メモリ最適化）の延長線として、**運用時間軸での負荷分離** を担う。

---

## 背景・なぜ今やるか

### 構造的盲点

```
phase17 で重量バッチ追加（PostalCsvImportJob / MonthlySalesReportJob 等）
  └─ EC2 t3.micro（vCPU 2 / 1 GB RAM）の同時実行能力が頭打ち
      └─ バッチ実行中の MySQL CPU 高騰 + Java GC 頻発
          └─ Market 顧客の購入操作・Console 管理者の操作が応答遅延
              └─ ユーザ体験が劣化（「壊れている」と誤認されるリスク）
                  └─ ポートフォリオ評価としてマイナス
```

### 現状

- 定期バッチ cron は深夜帯に集中（日次 03:30 / 月次 03:00 + 04:30 / 年次 1/1 05:00）
- ローカル開発環境（Docker Desktop / 多 vCPU / 16 GB RAM 級）では同時負荷を再現できない
- phaseX-4 軽負荷試験（3 経路 × 60 秒・180/180 200・OOM なし）はバッチ非実行時のもの
- バッチ実行中の Market アクセスを規制する仕組みは未実装
- バッチ実行中であることを示すフロントエンド表示も未実装

### 仮説（要検証）

| 想定シナリオ | 想定負荷 | t3.micro での挙動 |
|------------|---------|------------------|
| `PostalCsvImportJob` 取込中 | 12 万件 INSERT × 10〜15 分・MySQL CPU 高負荷 | API 応答 5〜30 秒、最悪タイムアウト |
| `MonthlySalesReportJob` 集計中 | 集計 SQL（規模次第） | API 応答数秒の遅延スパイク |
| `InventoryConsistencyCheckJob` 日次 | SKU TX 集計 SQL | 03:30 JST 深夜帯のため業務時間外 |
| 上記 + Market 顧客同時アクセス | I/O・コネクション競合 | 表示崩れ・タイムアウト・空配列レスポンス |

t3.micro 上で実測する前に**先回りで対策を入れる**。本フェーズは「予防的設計」フェーズと位置付ける。

---

## スコープ

| 項目 | スコープ内 | スコープ外（次フェーズ送り） |
|------|----------|------------------------|
| メンテナンス時間帯の定義（環境変数 + config） | ✅ | — |
| Market フロントのメンテナンスオーバーレイ表示 | ✅ | — |
| Console 側はメンテ表示せず、admin のみアクセス可制御 | ✅ | — |
| Core API レイヤでの「メンテ時間帯リクエスト拒否」フィルタ | ✅ | — |
| 業務 API と認証 API（ログイン）の分離（メンテ中もログインだけは通す） | ✅ | — |
| バッチ完了後の自動メンテ解除（cron 連動 or 手動） | ✅（手動 / 時間ベースの両方） | — |
| バッチ実行中であることを Console 通知センターに INSERT する仕組み | ❌（既存の batch_executions で十分） | — |
| EC2 を t3.small へ昇格（負荷を吸収） | ❌（無料枠方針 / phaseX-4 と整合） | — |
| 業務時間帯の動的シフト（曜日別・祝日別） | ❌（YAGNI） | YAGNI |
| バッチ自体の chunk size / connection pool 分離による負荷平準化 | ❌（別軸の最適化） | phaseX-9 候補 |

---

## 設計案

### 1. メンテナンスウィンドウ定義

`config/maintenance.yml`（または application.properties）：

```properties
# JST 基準。HH:mm 形式。複数枠は CSV で。
amazia.maintenance.windows=02:30-05:30
# メンテ時間帯であってもアクセスを許可するロール（Console 管理操作の継続用）
amazia.maintenance.allowed-roles=admin,senior_admin,eternal_advisor
# Market 側のメンテ表示用メッセージ
amazia.maintenance.market-message=現在、システムメンテナンスのためご利用いただけません（02:30-05:30 JST 復旧予定）。
```

時間判定は `Asia/Tokyo` 固定（test_insights カテゴリ7-2 / R-13 と整合）。

### 2. Core API のメンテフィルタ

新設 `MaintenanceWindowFilter`（`OncePerRequestFilter`）：

```
リクエスト到着 → 現在時刻が maintenance.windows に該当するか判定
  ├─ 該当しない → 通常処理
  └─ 該当する
      ├─ /api/auth/login / /api/console/* （Console 系） → 認証済かつ allowed-roles なら通す
      ├─ /api/market/* （Market 系） → 503 Service Unavailable + maintenance ヘッダ付与
      └─ /actuator/health → 通す（外部監視のため）
```

レスポンスヘッダに `X-Amazia-Maintenance: true` と `Retry-After: <秒数>` を付与。

### 3. Market フロントのオーバーレイ表示

axios インターセプタで 503 + `X-Amazia-Maintenance: true` を検出 →
全画面オーバーレイ（Suspense 風）に切替。`maintenance.market-message` を表示。
オーバーレイは 60 秒ごとに `/actuator/health` を polling し、200 が返ったら自動解除。

### 4. Console 側の挙動

メンテ時間帯でも admin / senior_admin / eternal_advisor はログイン可・全画面操作可。
非 admin ロールがメンテ中にアクセスした場合は専用ページに遷移（「ログインは可能ですが
メンテナンス中のため一部機能はご利用いただけません」）。

### 5. メンテ時間外でのバッチ手動起動の扱い

phase17 の Console バッチ手動起動画面（`/batch/manual`）から admin がメンテ時間外に
重量バッチ（`PostalCsvImportJob` 等）を起動した場合、**確認ダイアログで警告を出す**：

```
PostalCsvImportJob は通常メンテ時間帯（02:30-05:30）に自動実行されます。
現在は業務時間内です。実行すると 5〜30 秒の API 応答遅延が発生する可能性があります。
実行しますか？
```

### 6. テスト方針

| カテゴリ | テスト |
|---------|------|
| Filter 単体 | メンテ時間帯判定（境界値含む）・URL パターンマッチング・ロール判定 |
| Filter 統合 | MockMvc で Core API へリクエスト → 503 / 200 の振り分け |
| Vue 統合 | axios mock で 503 + ヘッダを返した時にオーバーレイ表示 |
| E2E（手動） | t3.micro ステージングで 03:00 JST に PostalCsvImportJob 起動・別端末で Market アクセス → メンテ画面表示 |

---

## ステップ案

| # | ステップ | 対象 | 状態 |
|---|---------|------|------|
| 0 | phaseX-4 軽負荷試験を **バッチ実行中条件下で再実施**（現状把握） | core / EC2 | 🔲 |
| 1 | `MaintenanceWindowFilter` の実装 + JUnit | core | 🔲 |
| 2 | application.properties / docker-compose.yml に環境変数追加（規約 4-3） | core | 🔲 |
| 3 | Market フロント axios インターセプタ + オーバーレイコンポーネント | market | 🔲 |
| 4 | Console 側の admin 判定 + 非 admin 用メンテページ | console | 🔲 |
| 5 | バッチ手動起動画面の業務時間内警告ダイアログ | console | 🔲 |
| 6 | E2E（ステージング t3.micro で PostalCsvImportJob × Market アクセス） | 全体 | 🔲 |
| 7 | ドキュメント反映（phase17 §13.4 から本フェーズへの参照） | docs | 🔲 |

---

## 完了の定義

- Step 0 でバッチ実行中の応答遅延が定量化されている（t3.micro 実測値）
- Step 1〜5 のすべての TDD ケースが緑
- Step 6 で「Market 顧客が深夜帯にメンテ画面を見られること」が確認済
- 非メンテ時間帯では従来通りの動作（regression なし）
- phase17 設計書 §13.4 に「t3.micro バッチ実行中の応答遅延」が phaseX-8 で対応済として参照される

---

## リスクと対策

| リスク | 対策 |
|--------|------|
| ユーザがメンテ時間帯にアクセスして「壊れている」と誤認する | オーバーレイに復旧予定時刻を明示 |
| メンテ中にバッチが終わらず時間超過する | バッチ完了通知を `console_notifications` に出して人手対応導線を作る |
| メンテ時間帯の仕様変更（祝日・年末年始）が必要になる | YAGNI で初版は「JST 固定 02:30-05:30」のみ。将来要請があれば config 拡張 |
| 外部監視サービスがメンテ画面で誤検知 | `/actuator/health` はメンテ中も 200 を返す方針 |
| Market 顧客が決済中にメンテ突入 | 決済中（`carts.status=PROCESSING` 等）の顧客は除外、 or 次回ログインで「中断された注文があります」を表示 |

---

## 14. 申し送り（後続フェーズへ）

### 14-1. phaseX-9 候補：バッチ自体の負荷平準化
- `PostalCsvImportJob` の chunk INSERT サイズ縮小（現状 1000 → 200 検討）
- バッチ専用 JDBC Connection Pool（HikariCP の二重定義）
- `Hibernate.jdbc.batch_size` の見直し
- バッチ実行中の `@Transactional(timeout=...)` 明示

### 14-2. ロードバランス的なアプローチ
- t3.micro → t3.small への一時昇格を **メンテ時間帯のみ自動切替**（EventBridge + Lambda）。無料枠超過。
- バッチを別 EC2（オンデマンド起動）に分離。無料枠超過。

これらは無料枠方針（[user memory: free_tier_first]）と相反するため初版では採用せず、ユーザ規模拡大時の選択肢として残す。
