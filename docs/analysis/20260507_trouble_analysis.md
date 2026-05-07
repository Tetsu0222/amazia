# トラブル分析レポート（031〜040）

## 概要

`docs/troubles/` 配下の031〜040のトラブルドキュメントを「テストケース不足に起因するもの」と「環境設定上の課題」の2軸で分析する。
分析フレームワークは [20260503_trouble_analysis.md](20260503_trouble_analysis.md)・[20260504_trouble_analysis.md](20260504_trouble_analysis.md)・[20260505_trouble_analysis.md](20260505_trouble_analysis.md)・[20260506_trouble_analysis.md](20260506_trouble_analysis.md) を踏襲する。

> 注：本レポートは 031〜040 を対象とするが、その後 041〜044 も同期間（2026-05-06〜2026-05-07）の phase14 / phase14.5 / phase15 関連で連続発生しており、特に **027・037・038・044 の H2 と本番 MySQL のスキーマ乖離** が同型再発を続けているため、最終節で「次の品質改善フェーズに送る再発防止策」を別立てで整理する。

---

## 分類サマリー

| # | ファイル | タイトル | 分類 | 根本カテゴリ |
|---|---------|---------|------|------------|
| 031 | 031_console_cookie_relay_drops_set_cookie.md | Console（Laravel）の Cookie 中継で Set-Cookie が落ちる | テスト不足 + 環境設定 | Guzzle CookieJar 既定無効化 / 本番 HTTPS で初検証 |
| 032 | 032_jwt_alg_mismatch_console_vs_core.md | JWT 署名アルゴリズム不一致で API が 401 | テスト不足 | JJWT の鍵長依存挙動と Console 側 SHA-256 ハードコードの乖離 |
| 033 | 033_console_image_file_route_under_auth_jwt.md | `<img src>` 配信ルートが auth.jwt 配下で 401 | テスト不足 | 公開ルート規約の徹底不足（ブラウザの非 fetch コンテキスト想定外） |
| 034 | 034_phase13_no_incident_analysis.md | phase13 トラブル0件のメタ分析 | 分析メタ記録（テスト不足/環境設定の枠外） | 観測網の死角を含む3仮説の切り分け待ち |
| 035 | 035_market_checkout_sku_id_undefined.md | 購入ボタンで `sku_id=undefined` | テスト不足 | DTO getter 名と JSON フィールドの外挿による誤認 |
| 036 | 036_mui_grid_v1_to_v2_migration_missing.md | MUI v9 移行漏れ（Grid v1 構文 / Stack props 透過） | テスト不足 | Console 警告昇格の未整備 / メジャーバージョン移行手順の欠如 |
| 037 | 037_flyway_misassumed_phase14_tables_missing.md | Flyway 利用と誤認しフェーズ14 テーブルが本番 DB に作成されず | テスト不足 + 環境設定 | `db/migration/` 存在からの外挿による DB 初期化方式の誤認 |
| 038 | 038_products_price_stock_not_null_drift.md | products.price/stock NOT NULL 残存で Console 商品登録が 500 | テスト不足 + 環境設定 | H2/本番 MySQL のスキーマ乖離（旧 NOT NULL 残存） |
| 039 | 039_market_checkout_preorder_mode_missing.md | Market Checkout が preorder モード未対応 | テスト不足 | 画面間契約のテスト不在（Checkout テスト未整備） |
| 040 | 040_market_lists_products_without_sku_price.md | SKU 価格未登録の商品が Market 露出して 400 | テスト不足 | 「販売可否」概念の Service 層への組み込み欠落 |

---

## 詳細分析

### 031 — Console（Laravel）の Cookie 中継で Set-Cookie が落ちる（テスト不足 + 環境設定）

**分類：テストケース不足（Cookie 中継の実通信検証なし）＋ 環境設定上の課題（HTTPS 化で初出）**

Laravel HTTP クライアント（Guzzle ラッパー）は CookieJar が既定で無効のため `$response->cookies()` が空配列を返し、`refresh_token` が**ブラウザに到達しない**。トラブル020 で `domain=コンテナ名` を `null` 上書きする際に同パターンを採用していたが、本問題は phaseX-3（HTTPS 化）の本番動作確認まで顕在化しなかった。

- ローカル（HTTP）では別経路で動いていたため CI も実機ローカル確認も通っていた
- PHPUnit は Guzzle 応答をモックしているため実 Cookie 中継を検証していなかった

**再発防止**：Console は **Spring が返した `Set-Cookie` を生ヘッダで透過**する方針へ切替。Cookie 属性（Domain/Path/Secure/HttpOnly）の唯一の正本は Core 側 application properties。Console は属性を一切変更しない。030 派生 4 件（031・032・033 含む）と一体で「**本番環境で E2E（ログイン → リロード維持 → 画像表示）まで**」をフェーズ完了条件に格上げ。

---

### 032 — JWT 署名アルゴリズム不一致で API が 401（テスト不足）

**分類：テストケース不足（環境間で alg が変わる前提を契約で保証していなかった）**

JJWT の `Keys.hmacShaKeyFor()` は **シークレットの長さで alg を自動選択**（32B→HS256 / 48B→HS384 / 64B+→HS512）する。本番 `JWT_SECRET` は強度のため 64 バイト以上で **HS512**、ローカルは短い鍵で **HS256** に分岐していた。Console 側 `JwtVerifyService::verify` は `hash_hmac('sha256', ...)` をハードコードしていたため本番でだけ署名不一致 → 401。

- Console PHPUnit は固定 HS256 トークンの自己完結検証で、Spring 発行 JWT を使っていない
- Core JUnit は同じ JJWT で自己完結のため不整合に気付けない
- 「ローカルで動いて本番で落ちる」最も典型的な環境差バグ

**再発防止**：`JwtVerifyService::verify` を **JWT ヘッダの `alg` クレームに追従**する実装に変更。Spring 発行 JWT 文字列をフィクスチャとしてコミットし Console PHPUnit で検証する E2E 寄りテストを追加。`JWT_SECRET` は 64 バイト以上を本番標準として `setup.md` に明示。Spring 側 `JwtService` に「`Keys.hmacShaKeyFor()` の鍵長で alg が変わる」コメントを残す。

---

### 033 — Console 経由の SKU 画像配信が auth.jwt 配下にあり 401（テスト不足）

**分類：テストケース不足（公開ルート規約の徹底不足）**

ブラウザの `<img src>` は fetch API ではないため、JS で `axios.defaults.headers.common['Authorization']` を設定していても **Authorization ヘッダが付かない**。Console（Laravel）の `/skus/{id}/image-file/{path}` ルートは `routes/api.php` の `auth.jwt` グループ内に置かれていたため必ず 401 で弾かれていた。Market 経由（`/api/skus/.../image-file/...`）は公開ルートのため動作していたのと対照的。

- Console API テストは Authorization 付きリクエストでしか検証していなかった
- 「Authorization なしで `<img>` 経由のアクセスが通るか」の観点がテスト未整備
- 019（Console ログイン 500 / Market 401）で **公開ルートを auth グループ外に置く規約**は記録済みだったが、本ルートには適用が漏れていた（Console / Market のルート設計統一が言語化されていなかった）

**再発防止**：`/skus/{id}/image-file/{path}` を `auth.jwt` の**外**へ移動。**「ブラウザの非 fetch コンテキスト（`<img>` `<video>` `<a download>`）から呼ばれる可能性があるか」** をルート追加時のレビュー観点として `test_insights.md` カテゴリ11-2 に追加（履行済み）。Playwright 等で「Console 画像表示シナリオは Authorization 不要で 200」を E2E で保証する課題を別フェーズに送る。

---

### 034 — phase13 トラブル0件の不在分析（メタ記録）

**分類：本件は不具合ではなくメタ記録（テスト不足/環境設定の枠外）**

phase13（Market 認証 / DB 5テーブル / セッション認証 Filter / CSRF Filter / 顧客 API 5種 / SES / 郵便局 CSV / Market 5画面 / CloudFront Behavior 追加）にもかかわらず `docs/troubles/` への新規記載が 0 件という、過去フェーズと比較して構造的に異常な状態を**そのまま記録として残した**メタドキュメント。

仮説：
- A. 過去蓄積（test_insights / 033 までの教訓）が事前回避を効かせた（**ポジティブ**）
- B. 本番運用が薄く故障が露出していない（**ネガティブ**）
- C. 観測網の死角でサイレント故障が起きている可能性（**最も警戒すべき**）

A : B : C ≒ 4 : 3 : 3 の見立てで `🟡 様子見`。仮説 B/C を切り分けるための観測（CSRF 403 件数 / ロック解除ログ / SES バウンス通知 / `market_sessions` GC）整備は別フェーズに送る。

> 注：本件は分析の枠外だが「無事故 = 健全」と決めつけない姿勢そのものが、後の 037・038・044 のような **`continue-on-error` で潰された WARN 系の盲点**を意識する出発点になっている。後述「次フェーズの再発防止策」と接続する論点。

---

### 035 — Market 購入ボタンで `sku_id=undefined`（テスト不足）

**分類：テストケース不足（DTO 名の外挿による誤認）**

Core `SkuDetail` の getter は `getSkuId()` で Jackson シリアライズ後の JSON は **`skuId`** だが、Market 側 `ProductDetail.jsx` / `Checkout.jsx` は `selectedSku.id` を参照していたため `undefined` になり、URL が `?sku_id=undefined` で遷移。

- Vitest テストデータが `{ id: 101, ... }` で**実 JSON 形式と乖離**していたため、テスト上は緑だが実通信では破綻
- API レスポンスのフィールド名と Market テストデータの整合性チェック（コントラクトテスト）が不在
- 035 追記：その後 `ProductMarketSummary`（リスト API）に `skus` フィールドが**そもそも存在しない**ことが連鎖発覚。「全件取得→SKU 逆引き」のアンチパターンも併せて修正

**再発防止**：**Core 側 DTO（getter 名）を直接読んで JSON フィールド名を確定**してからフロントを書く規約。Vitest モックデータを Core DTO クラスに対応した JSON 形式へ揃える。新画面実装後はブラウザで手動 E2E（ボタンクリック → URL バー確認）を 1 周。識別子は URL や state で渡し、API 呼び出しは 1 回で完結させる設計を優先。

---

### 036 — MUI v9 移行漏れ（Grid v1 構文 / Stack props 透過）（テスト不足）

**分類：テストケース不足（ライブラリメジャーバージョン移行手順の欠如）**

2 系統が同時に放置されていた：

1. **Grid v1 構文の廃止**：`<Grid item xs={...}>` → `<Grid size={{ xs: 12, sm: 6, md: 4 }}>` への移行漏れ（`ProductList.jsx` 1 ファイル）
2. **Stack の `alignItems`/`justifyContent` props 透過**：MUI v9 で sx 経由必須に変わったのに直接 props で渡していた箇所が 7 ファイルに残存

- Vitest（jsdom + RTL）と実ブラウザ React 19 ランタイムで警告閾値が異なるためテストでは検知不能
- `console.error` を fail に昇格させる Vitest 設定がプロジェクトに未導入
- フェーズ9・10 から phase14 まで複数フェーズを跨いで放置されていた

**再発防止**：MUI / React Router / その他 UI ライブラリのメジャーバージョンアップ時、**Migration Guide を確認した上で `git grep` ベースで旧構文を全件検索**する手順を `docs/ai_context/` に整備。リリース前チェックリストに「ブラウザ Console タブの警告目視確認」を追加。Vitest での `console.error` 昇格は将来課題として記録。

> メタ評価論点：036 は「**過去フェーズで放置された UI 警告が複数フェーズを跨いで顕在化**」した最初の系統的事例。phase10 当時のトラブル蓄積量・知見充実度との相関は別途追跡（後日メタ評価タスクとしてドキュメント末尾に明記）。

---

### 037 — Flyway 利用と誤認しフェーズ14 テーブルが本番 DB に作成されず（テスト不足 + 環境設定）

**分類：テストケース不足 ＋ 環境設定上の課題（DB 初期化方式の前提誤認）**

本プロジェクトは **Flyway 未導入**（pom.xml に依存なし）にもかかわらず、`amazia-core/src/main/resources/db/migration/` の存在から「Flyway 利用」と外挿し、phase14 で `V6〜V11.sql` を作成していた。実態は `schema.sql` を `spring.sql.init.mode=always` で起動時実行する方式で、`db/migration/V*.sql` は名残ファイル（**死ファイル**）として無視されていた。

結果：
- `payment_methods` / `address` / `sales` / `sales_return` / `shipping_statuses` / `operation_logs` 6 テーブルが本番 MySQL に作成されず
- `mvn test` は H2 + `ddl-auto=create-drop` で Entity から自動生成されるためグリーン
- 注文確定 API（`POST /api/customer/orders/confirm`）が `payment_methods` 不在で 500

**035 と同型の "外挿による誤認" の再発**。035 では DTO 名を、037 では DB 初期化方式を、それぞれ**実コード/設定を直接読まずに前提を立てた**ことが直接原因。

**再発防止**：新フェーズ着手時に **`pom.xml` の依存関係 + 起動ログ** で Flyway / Liquibase / `schema.sql` のいずれが動いているかを直接観測する。`db/migration/` の存在は何の証拠にもならない。設計書に「前提」として書いた瞬間、それを裏付ける実コード/設定ファイルを 1 つ以上引用する規約を CLAUDE.md に追加する案を残す。`operational_insights.md` カテゴリ3 を「既存実装と環境設定の棚卸し」に拡張。

---

### 038 — products.price/stock NOT NULL 残存（テスト不足 + 環境設定）

**分類：テストケース不足 ＋ 環境設定上の課題（H2/本番 MySQL スキーマ乖離 — 027 の系譜）**

phase10 で価格・在庫を SKU 側（`product_sku_prices` / `product_sku_stocks`）に移行した際、旧 `products.price` / `products.stock` カラムは **設計書 / Entity 上は NULL 許容に変更したが、本番 MySQL の ALTER は実行されないまま放置**された。Vue ProductForm が `price`/`stock` を送らない（=設計通り SKU 側で管理）リクエストで MySQL 1048（`Column 'price' cannot be null`）で 500。

- Core JUnit は H2 + `ddl-auto=create-drop` で Entity から都度生成 → NULL 許容で再現される
- Console PHPUnit は `Http::fake` で Core 応答を偽装 → DB 制約に到達しない
- **本番 MySQL のみが旧 NOT NULL を保持**しており、UI 経由のリクエストでだけ顕在化

**再発防止**：schema.sql 末尾に冪等な `ALTER TABLE products MODIFY COLUMN price/stock INT NULL` を追記（次回起動で自動適用）+ 本番にホットフィックスで即時実行。`docs/database_design/TBL_products.md` 変更履歴に「フェーズ14.5 P2」として追記。`test_insights.md` に「本番 MySQL の NOT NULL 制約が H2 に伝わらない」観点を追記済。

---

### 039 — Market Checkout の preorder モード未対応（テスト不足）

**分類：テストケース不足（画面間契約のテスト不在）**

phase14.5 C-4 で予約フロー導線を追加（ProductDetail → `/checkout?...&preorder=1`）したが、**Checkout.jsx 側がクエリを読み取っていなかった**ため通常注文フォームと同じ動作（在庫超過警告で確定不可）になっていた。Core `OrderConfirmationService` は `request.isPreorder()` で在庫チェック/減算をスキップする実装だったが、その手前の Market Checkout が `preorder: false` を送り続けていた。

- C-4 完了時点で Checkout のテスト（`Checkout.test.jsx`）が**存在していなかった**
- ProductDetail のテストは「予約ボタン押下 → /checkout 画面が描画される」までしか確認していなかった
- 「画面間の連携」がテストの隙間に落ちた典型（**035 と同型**）

**再発防止**：Checkout に予約モード分岐を追加（タイトル / 数量ラベル / ボタン文言 / 在庫上限 / `confirmOrder.preorder`）し、新規テスト 7 ケースを `Checkout.test.jsx` に追加。**URL クエリ・ナビゲーション state を経由する画面遷移は、遷移先の画面でも単体テストで「クエリの解釈」「state の解釈」を検証**する規約を `test_insights.md` の「画面間契約テスト」項目に追加検討。

---

### 040 — SKU 価格未登録の商品が Market 露出して 400（テスト不足）

**分類：テストケース不足（Service 層に「販売可否」概念が無かった構造的問題）**

C-4 で「在庫 0 商品も Market に表示」を実装した副作用で、**SKU 価格未登録の商品まで露出**するようになった。Core `OrderConfirmationService` は `sku price not registered` で 400 を返す = 注文画面まで進ませてから初めて拒否する UX 破綻。Amazon / 楽天 / Yahoo! / ZOZO 等の業界標準は「**SKU・価格・在庫が揃って初めて出品可能**」が基本。

- 既存 `SkuAggregateControllerTest` の `setUp` は SKU 価格を必ず登録する**幸せパスのみ**だった
- 「価格未登録 SKU だけの商品」を Market から除外するというサーバ側の振る舞いは設計書にも明記されておらず、テスト観点として上がっていなかった
- 035・039 と同じ「画面間 / Service 間の契約に対するテスト欠落」

**再発防止**：`ListProductMarketService` に **`minPrice == null` 除外**ロジックを追加（一覧）/ **SKU 0 件 / 全 SKU 価格未登録のとき 404**（詳細）。UI 側も `minPrice == null` で価格行非表示（既存）と組み合わせて二重防御。Console 側の出品ワークフロー強化（`statusCode='ON_SALE'` 遷移条件に SKU 価格登録要求）は別フェーズに送る。

---

## 全体傾向と考察（031〜040）

### テストケース不足に起因するもの（8件）

| # | テストの空白 |
|---|------------|
| 031 | Console の実 Cookie 中継テスト不在（Guzzle CookieJar 既定無効化を見抜けず） |
| 032 | JWT alg 環境差のコントラクトテスト不在（Spring 発行 JWT を Console で検証していない） |
| 033 | `<img src>` 経由（Authorization なし）のアクセス可否のテスト観点不在 |
| 035 | DTO getter 名と JSON フィールド名のコントラクトテスト不在 |
| 036 | UI ライブラリメジャー移行時の旧構文 grep / コンソール警告昇格の未整備 |
| 037 | DB 初期化方式の前提（Flyway か schema.sql か）の検証手順不在 |
| 039 | URL クエリ経由のモード切替（preorder）の遷移先画面テスト不在 |
| 040 | Service 層の販売可否ロジックの欠落（幸せパスのみのテストデータ） |

### 環境設定上の課題（2件 — 031・037 のテスト不足と複合）

| パターン | 該当 |
|---------|------|
| ローカル HTTP / 本番 HTTPS の動作差で初動検証が漏れた | 031 |
| `db/migration/` の存在と実体（schema.sql 方式）の乖離 | 037 |

### H2 / 本番 MySQL のスキーマ乖離（1件 — 027 の再発系）

| # | 内容 |
|---|------|
| 038 | `products.price/stock` の NOT NULL 残存（H2 では Entity 通り NULL 許容生成） |

> 補足：本期間の直後（041〜044）で 037・038 と同型の **継続再発が 044 で 3 例目** となっている。最終節でまとめて扱う。

### メタ記録（1件）

| # | 内容 |
|---|------|
| 034 | phase13 で「無事故」だったこと自体を分析。観測網の死角を将来課題として保留 |

---

## 031〜040 の構造的パターン

### パターンE：phaseX-3 動作確認で連鎖発見された潜在不具合（030 → 031・032・033）

030 はそれ自体は構成変更だが、「**本番で初めて Console ログイン以降を実機検証した**」ことで **Cookie 中継 / JWT alg / 画像配信ルート** 3 件の潜在不具合が同時に出現。「**本番環境で E2E（ログイン → リロード維持 → 画像表示）まで**」をフェーズ完了条件に格上げした。

```
030（構成変更）
  ├─ 031: Cookie 中継不備（Guzzle CookieJar）
  ├─ 032: JWT alg 不一致（鍵長で alg が変わる）
  └─ 033: 画像配信ルートが auth 配下
```

### パターンF：phase14 / phase14.5 の「画面間契約」テスト不在の連鎖（035 → 039 → 040 → 041）

035 で「Core DTO を直接読まないと外挿で外す」教訓が抽出されたにもかかわらず、

- 039：URL クエリ（`?preorder=1`）の遷移先での解釈漏れ
- 040：Service 層の販売可否概念の欠落
- 041：公開判定の二重基準（秒単位 vs JST 0:00）

と**異なるレイヤーで同型の「画面間 / Service 間の契約に対するテスト欠落」**が連鎖。035 → 037 で「外挿による誤認」が **DTO レイヤー → 環境設定レイヤー** に拡張したのと同じく、**個別バグへの対症療法では同型再発が止まらない**ことを示している。

### パターンG：H2 / 本番 MySQL のスキーマ乖離 が継続再発（027 → 037 → 038 → 044）

| # | 乖離の種類 |
|---|----------|
| 027 | `schema.sql` の `INDEX` インライン句が H2 で「不明なデータ型」 |
| 037 | `db/migration/V*.sql` が本番では実行されない（Flyway 誤認） |
| 038 | 旧 `products.price/stock` の NOT NULL が本番 MySQL に残存 |
| 044 | `operation_logs.user_id BIGINT` と本番 `users.id BIGINT UNSIGNED` の FK 型不整合（DDL が `continue-on-error` で潰される） |

H2 + `ddl-auto=create-drop` のテストでは検知不能な系統的盲点が**4 例目**まで来ており、後述の「次フェーズに送る再発防止策」の中心トピックになる。

### パターンH：複数フェーズを跨いだ放置（036）

MUI v9 移行漏れ（Grid v1 構文 / Stack props 透過）は phase9・10 から phase14 までの間、複数フェーズに渡って放置されていた。リリース前のブラウザ Console 警告目視確認がチェックリストに入っていれば早期発見できたバグ。phase10 当時のトラブル蓄積量・知見充実度との相関は後日メタ評価対象。

---

## 001〜040 通算の構造的パターン

5月3日（001-006）／5月4日（007-013）／5月5日（014-020）／5月6日（021-030）／5月7日（031-040）の5レポート合算で 40 件全体に下記のパターンが継続している。

| パターン | 001-006 | 007-013 | 014-020 | 021-030 | 031-040 |
|---------|---------|---------|---------|---------|---------|
| デプロイ後ヘルスチェック不在 | 003, 005 | 008, 010, 011, 013 | 014, 016 | （028 で派生） | 037, 038（044 で 3 例目） |
| docker-compose / 環境変数管理漏れ | 002, 004 | 008, 009, 010 | 015, 018 | 023, 029 | — |
| フロント / UI の実装検証不在 | — | 007, 011, 012, 013 | 014 | — | 035, 036, 039, 040 |
| 認証・Cookie・プロキシ層の設計検証不在 | — | — | 019, 020 | 021 | 031, 032, 033 |
| AWS 運用（コスト・ディスク・配信統合） | — | — | 016, 017 | （X-3 で対応） | （042 で 3 例目） |
| **SSM デプロイ機構の連鎖補強** | 001 | — | — | 022, 024, 025, 026 | （042 で連鎖） |
| **本番 MySQL と Laravel/Spring の DB 共有問題** | — | — | 018 | 029 | — |
| **CD と systemd の経路不整合** | — | 008（systemd 導入） | — | 023, 028, 029 | — |
| **H2 / 本番 MySQL のスキーマ乖離**（新規系統） | — | — | — | 027 | 037, 038（044 で 3 例目） |
| **画面間 / Service 間契約のテスト不在**（新規系統） | — | — | — | — | 035, 036, 039, 040（041 で連鎖） |
| **phaseX-3 / X-4 で本番初動が連鎖発見**（新規系統） | — | — | — | 030 | 031, 032, 033 |

031〜040 で新たに浮上したのは：
1. **画面間 / Service 間契約のテスト不在**（035・036・039・040）- 041 でさらに連鎖
2. **H2 / 本番 MySQL のスキーマ乖離が系統的盲点として確定**（027 → 037 → 038、44 で 4 例目）
3. **本番 HTTPS 化（X-3）で初動の認証/Cookie/画像配信が連鎖発見**（031・032・033）
4. **phase13 トラブル0件の不在分析というメタ視点の導入**（034）

---

## 推奨アクション（031〜040 追加分）

| 優先 | 内容 | 関連 |
|------|------|------|
| 高 | **デプロイ後ヘルスチェックに「主要テーブル存在確認 SQL（SHOW TABLES / SELECT 1 FROM ... LIMIT 1）」を1ステップ追加**。`continue-on-error` で潰された WARN を**デプロイ後に検知**する仕組みを次回品質改善フェーズで導入 | 027, 037, 038, 044 |
| 高 | Core 起動ログの WARN を CD ジョブログに `grep` で抽出するステップを deploy.yml に追加（`continue-on-error` の盲点に対する直接対策） | 037, 038, 044 |
| 高 | 「本番環境で E2E（ログイン → リロード維持 → 画像表示）まで」をフェーズ完了条件に格上げ | 030, 031, 032, 033 |
| 高 | `<img>` `<video>` `<a download>` 等のブラウザ非 fetch コンテキストから呼ばれるルートは公開規約に則って auth グループ外に置く（既に test_insights カテゴリ11-2 に追記済） | 033 |
| 中 | Spring 発行 JWT を Console PHPUnit でフィクスチャ検証する E2E 寄りテストを導入。`JWT_SECRET` 64 バイト以上を `setup.md` に明記 | 032 |
| 中 | フロントの Vitest モックデータを Core DTO の実 JSON 形式へ揃える運用（getter から外挿しない）。可能なら Core 側に「DTO のサンプル JSON を出力するテスト」を置きフロントの参照元にする | 035, 039, 040 |
| 中 | 設計書「前提」セクションに **裏付け参照ファイル** を必須項目として明記する規約を CLAUDE.md に追加検討 | 037 |
| 中 | URL クエリ・ナビゲーション state を経由する画面遷移は、遷移先の画面でも単体テストで「クエリの解釈」「state の解釈」を検証する観点を `test_insights.md` の「画面間契約テスト」項目に追加 | 039 |
| 中 | UI ライブラリのメジャーバージョンアップ手順（Migration Guide 確認 → 旧構文 grep → ブラウザ Console 警告目視）を `docs/ai_context/` に整備 | 036 |
| 低 | `console.error` を fail に昇格させる Vitest 設定（`@testing-library/react`）の導入を将来課題として記録 | 036 |
| 低 | `operational_insights.md` カテゴリ3 を「既存実装と環境設定の棚卸し」に拡張 | 037 |
| 低 | phase13 の観測網の死角（CSRF 403 件数 / ロック解除ログ / SES バウンス通知 / `market_sessions` GC）の整備を別フェーズに送る | 034 |

---

## 次の品質改善フェーズに送る再発防止策（重点トピック）

> 本節は 031〜040 のスコープを少し外れるが、027・037・038 に続き **044 で H2 と本番 MySQL のスキーマ乖離が 4 例目** に到達したこと、および 044 で **「`continue-on-error` で潰された WARN がデプロイ後に検知できない」** という構造的盲点が顕在化したことを踏まえ、次の品質改善フェーズで取り組む対策をここに別立てで整理する。

### 構造的盲点の整理

```
schema.sql の DDL 失敗
  └─ continue-on-error=true で WARN として潰される
      └─ Core 起動は成功（プロセスは生存）
          └─ 該当テーブルが呼ばれた瞬間に 1146 / 23000 / 等で 500
              └─ ユーザーが踏むまで気付けない（サイレント故障）
```

実例（時系列）：
- 027（2026-05-06）：`schema.sql` の MySQL 専用構文が H2 で爆発（テスト時検知できた版）
- 037（2026-05-06）：`db/migration/V*.sql` が死ファイル化（テーブル不在）
- 038（2026-05-07）：`products.price/stock` の NOT NULL 残存（旧 ALTER 漏れ）
- 044（2026-05-07）：`operation_logs.user_id` の signed/unsigned 型不整合（FK 失敗）

### 再発防止策

#### 1. デプロイ後ヘルスチェックに「主要テーブル存在確認」を含める（最優先）

deploy.yml の `canary_check` 通過後、または `docker compose up -d` 直後に、Core コンテナから MySQL に対し以下の確認を 1 ステップとして実施する：

```sql
-- 主要テーブル存在確認
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'amazia'
  AND table_name IN (
    'products', 'product_skus', 'product_sku_prices', 'product_sku_stocks',
    'users', 'permissions', 'roles', 'role_permissions',
    'payment_methods', 'address', 'sales', 'sales_return',
    'shipping_statuses', 'deliveries', 'operation_logs',
    'workflows', 'market_customers', 'market_sessions'
  );
```

期待件数（≥ N）が満たされない場合は CD ジョブを `exit 1` で失敗させる。`continue-on-error` で潰された DDL 失敗を**デプロイ後に検知**でき、044 のような「呼ばれるまで気付けないサイレント故障」を防げる。

実装イメージ（deploy.yml 追加ステップ）：
```yaml
- name: ヘルスチェック - 主要テーブル存在確認
  run: |
    EXPECTED_TABLES="products,product_skus,...(上記一覧)"
    EXPECTED_COUNT=$(echo "$EXPECTED_TABLES" | tr ',' '\n' | wc -l)
    ACTUAL_COUNT=$(aws ssm send-command --document-name AWS-RunShellScript ... \
      --parameters 'commands=["docker exec amazia-mysql mysql -uroot -p... -N -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=\\\"amazia\\\" AND table_name IN (...)\""]' \
      ...)
    if [ "$ACTUAL_COUNT" -lt "$EXPECTED_COUNT" ]; then
      echo "ERROR: 主要テーブルが $EXPECTED_COUNT 件中 $ACTUAL_COUNT 件しか存在しない"
      exit 1
    fi
```

主要テーブル一覧は `docs/database_design/README.md` の表から自動生成する案も検討（リスト保守の二重管理を避ける）。

#### 2. Core 起動ログの WARN を CD ジョブログに抽出

`continue-on-error` で潰された DDL エラーは Core ログに **WARN レベル**で出ている。デプロイ完了直後にコンテナログから該当 WARN を `grep` してジョブログに表示する：

```bash
docker logs amazia-core 2>&1 | grep -E 'WARN.*sql.init|WARN.*schema|WARN.*DDL' | tee /tmp/schema_warn.log
if [ -s /tmp/schema_warn.log ]; then
  echo "::warning::schema.sql 実行時に WARN が検出されました。確認してください。"
fi
```

これは「失敗扱いにはしない」運用も選べる柔軟な対策。1. と組み合わせれば **WARN を見逃さない + 主要テーブルは存在保証** の二段構えになる。

#### 3. schema.sql 編集時のレビュー観点 (チェックリスト)

`docs/ai_context/operational_insights.md` または coding_guidelines に以下を追加：

- FK 列の signed/unsigned を参照先と一致させる（特に `users.id` は **BIGINT UNSIGNED**）
- `CREATE TABLE IF NOT EXISTS` / `ALTER TABLE` の冪等性を確認する
- 新規テーブルは `docs/database_design/TBL_*.md` を**同フェーズ内で**更新（CLAUDE.md 規約と接続）
- H2 / MySQL 互換性を意識した DDL（`INDEX` インライン句禁止 等、027 起因）

#### 4. 本番 MySQL のスキーマスナップショットを定期取得

将来課題として、`mysqldump --no-data` で本番のスキーマ DDL を 1 日 1 回 S3（無料枠）に保存し、`schema.sql` との差分を週次でレビューする運用。これは Entity と本番 MySQL の継続的な差分検知の現実的な手段で、有料の DB 比較ツールに頼らずに無料枠完走方針と整合する。

### 期待効果と次のフェーズへの引き継ぎ

- 1 が入れば 037・044 のような「テーブル不在」系トラブルは**デプロイ後 1 分以内に検知可能**
- 2 が入れば 038 のような「制約ドリフト」系トラブルも WARN ログで先行検知できる可能性が高まる（MySQL 1048 は実行時のみ出るため完全ではない）
- 3 / 4 は再発防止の規律としての底上げ

次フェーズの実装計画書として **[phaseX-6_post_deploy_schema_healthcheck.md](../design/phaseX/phaseX-6_post_deploy_schema_healthcheck.md)** に上記 1〜4 を移植し、コーディング規約 / CLAUDE.md / `test_insights.md` / `operational_insights.md` の追記まで含めて1パッケージで対応する方針で着手予定（2026-05-07 起票）。

> 本節は次フェーズ着手時の出発点として独立した粒度で書いた。実装の詳細はリンク先の phaseX-6 設計書を参照。
