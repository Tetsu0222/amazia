
# フェーズ13：Amazia Market ログイン・会員登録機能（最終改訂版 / 2026-05-06 改訂2）

## ステータス
🔲 未着手（設計確定 / DB 設計まで完了予定）

## 改訂履歴
- 2026-05-06 改訂2：X-3 完了反映 / 無料枠方針反映（Redis 廃止・セッションは DB 直）/ Market が React である事実反映 / テーブル名確定 / API プリフィックス確定
- 初版：ALB + ACM + Redis 前提

## 範囲
- Amazia Market（**React + MUI** SPA）
- Amazia Core（Spring Boot）
- DB 設計（Market 顧客 / 住所マスタ / セッション / パスワード再発行トークン）
- メール送信基盤（SES）

---

# 1. 機能概要

- Amazia Market にログイン機能・会員登録機能・パスワード再発行機能を追加
- HTTPS 化は **フェーズX-3 で完了済み**（CloudFront + desec.io / 1ドメイン構成、`https://www.amazia-portfolio.dedyn.io/`）
- 郵便番号から住所を自動反映する仕組みを導入（Core で郵便局 CSV を取り込み）
- メール送信は Amazon SES を採用（X-3 の残課題：サンドボックス解除 / 送信元検証も本フェーズで完了）
- **AWS 無料枠完走方針**を主軸に置き、Redis（ElastiCache）/ DynamoDB は採用しない（DB 直で完結）

---

# 2. HTTPS 化（X-3 で完了済）

> **2026-05-06 X-3 完了：** 本フェーズで HTTPS 化は実施しない。X-3 設計書の構成で稼働済み。
> 詳細は [phaseX-3 設計書](../phaseX/phaseX-3_https_via_cloudfront.md) を参照。

## 2.1 X-3 で確定済みの構成
- 正規 URL: `https://www.amazia-portfolio.dedyn.io/`
- DDNS: desec.io（DuckDNS 不採用 → ACM 検証用 CNAME を作れず断念した経緯あり）
- CloudFront → EC2（HTTP）。ACM 証明書は us-east-1 で発行済み
- HTTP → HTTPS リダイレクトは CloudFront Viewer Protocol Policy で実装済み
- 直 IP `https://13.54.203.95/` は nginx で 444 切断

## 2.2 本フェーズで CloudFront に追加する Behavior

X-3 既存の Behavior に、Market 顧客 API 用 Behavior を 1 本追加する。

| 優先度 | Path Pattern | Origin | キャッシュ | Cookie 転送 | 備考 |
|--------|-------------|--------|-----------|------------|------|
| 1 | `/console/api/*`        | EC2:8000 | 無効 | 全転送 | 既存（Console API） |
| 2 | `/console/*`            | EC2:8001 | 有効 | なし   | 既存（Console SPA） |
| 3 | `/api/customer/*`       | EC2:8080 | 無効 | 全転送 | **新規（Market 顧客 API）** |
| 4 | `/api/*`                | EC2:8080 | 無効 | 全転送 | 既存（Core API） |
| Default | `*`                | EC2:80   | 有効 | なし   | 既存（Market 静的） |

**留意：** `/api/customer/*` は `/api/*` より優先度を高くする。CloudFront は最長一致ではなく優先順位順評価のため。

---

# 3. 認証方式（確定仕様）

## 3.1 方式
- **セッション方式（Cookie ベース）を採用**
- Console（社員）は JWT、Market（顧客）はセッションで**役割を分離**
- セッションストアは **DB 直**（Spring Session JDBC ではなく、シンプルな `market_sessions` テーブル + 自作 Filter で管理。Spring Session 依存追加でコンテナメモリを増やさない方針）

## 3.2 Cookie 設定
| 属性 | 値 |
|------|---|
| Name | `MARKET_SESSION_ID` |
| Secure | true（X-3 で HTTPS 化済のため） |
| HttpOnly | true |
| SameSite | Lax |
| Domain | `www.amazia-portfolio.dedyn.io` |
| Path | `/` |
| 有効期限 | 30 分（操作で延長 / sliding） |

**HTTP 直 IP 運用時のフォールバック：** X-3 の `HTTPS_ENABLED` フラグと同じ思想で、`MARKET_COOKIE_SECURE` / `MARKET_COOKIE_DOMAIN` を環境変数で切替可能にする。

## 3.3 CSRF 対策
- セッションテーブルに `csrf_token` カラムを持たせ、ログイン時に発行
- フロントは `GET /api/customer/csrf-token` で取得、ヘッダ `X-CSRF-Token` に付与して送信
- 有効期限はセッションと同じ
- **Spring Security の CsrfFilter は使わず**、自作 Filter で `X-CSRF-Token` ヘッダを検証（Console と認証経路を分けるため）

## 3.4 ログイン試行回数制限
- **5 回失敗で 5 分間アカウントロック**
- `market_customers.failed_attempts` / `market_customers.locked_until` で管理
- CloudWatch + WAF による IP ブロックは将来課題

---

# 4. ログイン画面（Amazia Market）

- メールアドレス + パスワードでログイン
- 認証成功後、ユーザーマイページ（`/mypage`）へ遷移
- ログイン失敗時はエラーメッセージ表示
- ログイン成功/失敗はログに記録（メールアドレスはマスク：`a***@example.com`）

### ルート（React Router）
| パス | 用途 |
|------|------|
| `/login` | ログイン画面 |
| `/register` | 会員登録画面 |
| `/password/reset` | パスワード再発行リクエスト |
| `/password/reset/confirm` | パスワード再発行完了画面（メールリンク先） |
| `/mypage` | マイページ（認証必須） |

---

# 5. 会員登録画面（Amazia Market）

## 5.1 入力項目
- 名前（姓・名）
- 住所（郵便番号 → 自動補完）
- 生年月日（YYYY-MM-DD）
- メールアドレス
- パスワード / パスワード確認
- 決済方法（プルダウン）
  - クレジットカード
  - 銀行振込
  - その他（DB 管理）

## 5.2 バリデーション

### パスワードポリシー
- 8 文字以上
- 英大文字・英小文字・数字を含む
- 過去 5 回分のパスワードは再利用不可（`market_customer_password_histories`）

### 生年月日
- 18 歳未満は登録不可（サーバー側でも検証）

### メールアドレス重複チェック
- Ajax で事前チェック（`GET /api/customer/email-availability?email=...`）
- DB 側でもユニーク制約
- エラー文言：「このメールアドレスは既に登録されています」

## 5.3 クレジットカード情報
- **カード番号は保存しない（PCI DSS 準拠）**
- 外部決済代行サービスの**トークン化方式**を採用
- DB にはカードトークンのみ保存（`market_customers.card_token`）
- 外部決済代行サービスの選定は本フェーズ範囲外（モックトークン `mock_xxx` で代替）

## 5.4 郵便番号 → 住所自動反映（UI 仕様）
- 郵便番号入力（7 桁、半角数字 / ハイフン任意）後、debounce 300ms で `GET /api/customer/postal-addresses?postal_code=xxx`
- 候補が複数ある場合はプルダウンで選択
- エラー時は「住所が取得できませんでした」と表示

---

# 6. パスワード再発行フロー

## 6.1 フロー
1. メールアドレス入力（`/password/reset`）
2. ワンタイムリンクをメール送信（SES）
3. リンククリックで本人確認（`/password/reset/confirm?token=xxx`）
4. パスワード再設定画面で新パスワード入力
5. パスワード更新 → 自動ログインせずログイン画面へ誘導

## 6.2 トークン仕様
- 有効期限：30 分
- 使い捨て（`used` フラグ）
- 保存場所：**`market_customers_password_reset_tokens` テーブル（DB 直）**
- トークン本体は **bcrypt ハッシュ**して保存（生トークンはメール内 URL のみに存在）
- メール内 URL は独自ドメイン固定：`https://www.amazia-portfolio.dedyn.io/password/reset/confirm?token=xxx`

---

# 7. Amazia Core 側の実装

## 7.1 郵便局 API 連携
- 郵便局公式 CSV（[KEN_ALL.CSV](https://www.post.japanpost.jp/zipcode/dl/oogaki-zip.html)）を毎月 1 回取得
- **全件洗い替え方式**（TRUNCATE → INSERT）
- バッチ失敗時は 3 回リトライ
- CloudWatch に更新ログを出力

## 7.2 バッチ実行環境
- **Spring `@Scheduled` ジョブ**（amazia-core コンテナ内で実行）
- ECS Scheduled Task / Lambda は無料枠を考慮し採用しない
- 実行時間帯：毎月 1 日 03:00 JST
- 失敗時は CloudWatch Logs にエラーログ出力（SNS 通知は将来課題）

## 7.3 認証 / セッション関連の実装場所
- パッケージ：`com.example.market.customer.*`（既存 `auth` パッケージは社員専用なので分離）
  - `controller/`：`RegisterCustomerController` `LoginCustomerController` `LogoutCustomerController` `PasswordResetRequestCustomerController` `PasswordResetConfirmCustomerController` `EmailAvailabilityController` `MyPageController`
  - `service/`：1 ファイル 1 ユースケース
  - `entity/`：`Customer` `CustomerPasswordHistory` `CustomerPasswordResetToken` `MarketSession`
  - `repository/`：上記 4 つの JpaRepository
  - `filter/`：`MarketSessionAuthFilter`（Cookie 検証）/ `MarketCsrfFilter`
- パッケージ：`com.example.market.postal.*`（住所マスタ）
  - `entity/PostalAddress`、`service/ImportPostalCsvService`、`controller/SearchPostalAddressController`

---

# 8. DB 設計（最終版）

## 8.1 `market_customers` テーブル（顧客マスタ）

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT UNSIGNED PK | |
| name_last | VARCHAR(100) NOT NULL | 姓 |
| name_first | VARCHAR(100) NOT NULL | 名 |
| postal_code | VARCHAR(8) NOT NULL | 郵便番号（ハイフンなし 7 桁） |
| address | VARCHAR(255) NOT NULL | 住所（建物名含む） |
| birthday | DATE NOT NULL | 生年月日 |
| email | VARCHAR(255) NOT NULL UNIQUE | メールアドレス |
| password_hash | VARCHAR(255) NOT NULL | bcrypt |
| payment_method | VARCHAR(20) NOT NULL | `credit_card` / `bank_transfer` / `other` |
| card_token | VARCHAR(255) NULL | 決済代行サービスのトークン |
| active_flag | BOOLEAN NOT NULL DEFAULT TRUE | 退会フラグ |
| failed_attempts | INT NOT NULL DEFAULT 0 | ログイン失敗回数 |
| locked_until | DATETIME NULL | アカウントロック解除時刻 |
| created_at | DATETIME NOT NULL | |
| updated_at | DATETIME NOT NULL | |

**インデックス：** email（UNIQUE）

## 8.2 `market_customer_password_histories` テーブル

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT UNSIGNED PK | |
| customer_id | BIGINT UNSIGNED NOT NULL FK | |
| password_hash | VARCHAR(255) NOT NULL | |
| created_at | DATETIME NOT NULL | |

**インデックス：** customer_id

## 8.3 `market_customers_password_reset_tokens` テーブル

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT UNSIGNED PK | |
| customer_id | BIGINT UNSIGNED NOT NULL FK | |
| token_hash | VARCHAR(255) NOT NULL UNIQUE | |
| expires_at | DATETIME NOT NULL | |
| used | BOOLEAN NOT NULL DEFAULT FALSE | |
| created_at | DATETIME NOT NULL | |

**インデックス：** customer_id, token_hash（UNIQUE）

## 8.4 `market_sessions` テーブル（セッションストア）

| カラム名 | 型 | 説明 |
|---------|-----|------|
| session_id | VARCHAR(64) PK | UUID v4（Cookie 値） |
| customer_id | BIGINT UNSIGNED NOT NULL FK | |
| csrf_token | VARCHAR(64) NOT NULL | |
| expires_at | DATETIME NOT NULL | |
| created_at | DATETIME NOT NULL | |
| last_accessed_at | DATETIME NOT NULL | sliding 用 |

**インデックス：** customer_id, expires_at

## 8.5 `postal_addresses` テーブル（住所マスタ）

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | BIGINT UNSIGNED PK | |
| postal_code | VARCHAR(8) NOT NULL | 郵便番号（ハイフンなし 7 桁） |
| prefecture | VARCHAR(20) NOT NULL | 都道府県 |
| city | VARCHAR(100) NOT NULL | 市区町村 |
| town | VARCHAR(200) NOT NULL | 町域 |
| updated_at | DATETIME NOT NULL | |

**インデックス：**
- `idx_postal_code (postal_code)`
- `idx_pref_city (prefecture, city)`

**留意：** 1 郵便番号に複数町域がある（東京都千代田区など）ため複合 UNIQUE は張らない。

---

# 9. AWS 無料利用枠（Free Tier）方針

## 9.1 確定方針：採用しないサービス（無料枠外 or 維持コスト発生）

| サービス | 不採用理由 | 代替 |
|---------|-----------|------|
| ALB | 時間課金 $16/月（無料枠外） | **CloudFront（X-3 で実装済）** |
| ElastiCache (Redis) | 無料枠外 | **DB 直（`market_sessions` テーブル）** |
| DynamoDB | 無料枠はあるが学習目的・運用基盤分離コスト増 | DB 直 |
| ECS Fargate | 無料枠外 | EC2 t3.micro（X-4 復帰済） |
| ECS Scheduled Task / Lambda（住所マスタバッチ） | コールドスタート・運用分散 | **Spring `@Scheduled`** |

## 9.2 採用するサービス
- CloudFront（既存 X-3、Behavior 1 本追加）
- EC2 t3.micro（既存 X-4）
- RDS は使わず EC2 上の MySQL コンテナ（既存）
- SES（無料枠：EC2 経由 62,000 通/月）
- Route 53 不採用（desec.io / X-3 で確定）

---

# 10. SES 運用

## 10.1 サンドボックス解除（X-3 残課題と統合）
- AWS サポートに申請
- 利用目的・送信ドメイン（`www.amazia-portfolio.dedyn.io`）・想定送信量（< 100 通/日）を記載
- 送信元アドレス：`no-reply@amazia-portfolio.dedyn.io`（ドメイン認証 SPF / DKIM / DMARC を desec.io 側に設定）

## 10.2 送信制限
- レートリミットを CloudWatch で監視（既定 14 通/秒、無料枠は十分余裕）
- バースト時のキューイング（SQS）は将来課題

## 10.3 送信失敗時のフォールバック
- リトライ（指数バックオフ：1s → 2s → 4s）
- 3 回失敗時はログ出力 + ユーザーには「メール送信に失敗しました。時間をおいて再度お試しください」と表示
- 長期障害時の SendGrid フェイルオーバーは将来課題（無料枠 SendGrid は廃止済のため、別 SES アカウント or 手動運用）

---

# 11. テスト観点（最終版）

## 11.1 セキュリティテスト
- CSRF（X-CSRF-Token なし → 403）
- XSS（React の自動エスケープに加え、サーバー側でも入力サニタイズ）
- SQL Injection（JPA / PreparedStatement で防御済の確認）
- セッション固定攻撃（ログイン成功時にセッション ID を再発行）
- Cookie 属性検証（Secure / HttpOnly / SameSite）
- パスワードハッシュ強度（bcrypt cost 12 以上）
- トークン改ざん検知（`token_hash` 比較）
- ログイン試行回数制限（5 回 → ロック / 5 分後解除）

## 11.2 パフォーマンステスト
- 郵便番号検索 API：200ms 以下
- 会員登録 API：1 秒以内
- 住所マスタバッチ：10 分以内（KEN_ALL.CSV 約 12 万件）

## 11.3 H2 互換性（カテゴリ7-2 027 教訓）
- 新規 schema は **JPA Entity だけで H2 にスキーマ生成可能**な範囲に収める
- MySQL 専用構文（`ON UPDATE CURRENT_TIMESTAMP` / インライン INDEX）は **migration（V5）** にのみ書き、テスト H2 には流さない
- `application-test.properties` で `schema-locations=` 空指定の方針を維持

## 11.4 環境変数管理（009 教訓）
- 新規環境変数（`MARKET_COOKIE_SECURE` / `MARKET_COOKIE_DOMAIN` / `MARKET_SESSION_TTL_SECONDS` / `POSTAL_CSV_URL` 等）は
  - `docker-compose.yml`
  - `application-dev.properties`
  - `application-test.properties`
  - `.env.example`（存在すれば）
  をセットで更新する

## 11.5 Docker 初回起動（018 教訓）
- フェーズ完了時に `docker compose down -v && docker compose up --build` で起動できることを確認

---

# 12. ログ設計

- ログイン成功 / 失敗ログ（メールアドレスは `a***@x.com` でマスク）
- 会員登録ログ
- パスワード再発行リクエスト / 完了ログ
- 個人情報を含むログは KMS 暗号化（将来課題、本フェーズでは PII 自体をログに出さない設計で代替）
- CloudWatch Logs に集約、保持期間 1 年

---

# 13. 障害時の復旧手順

## SES 障害時
- リトライ（指数バックオフ）
- 長期障害時はログ出力のみ、ユーザーへは「再試行」案内

## DB 障害時
- 本番は EC2 上の MySQL コンテナのため Multi-AZ なし
- 復旧後は整合性チェック（`market_sessions` の `expires_at` 切れレコードを `DELETE`）
- セッション切れになるため全顧客がログアウト状態に戻る点は許容

---

# 14. ステップ一覧

| # | ステップ | 対象 | ステータス |
|---|---------|------|-----------|
| 0 | 設計書 X-3 反映・Redis→DB 直に改訂 | docs | ✅ 本ドキュメント |
| 1 | DB マイグレーション V5（`market_customers` 等 5 テーブル） | core | 🟡 本セッション |
| 2 | JPA Entity / Repository 実装 | core | 🔲 |
| 3 | セッション認証 Filter（自作 + CSRF） | core | 🔲 |
| 4 | 顧客 API 実装（登録 / ログイン / マイページ / パスリセ） | core | 🟡 パスリセ実装済（登録/ログイン/マイページ済 → 残: SES サンドボックス解除は §10.1 で扱う） |
| 5 | 郵便局 CSV 取込バッチ + 検索 API | core | 🔲 |
| 6 | SES 連携（送信ロジック + サンドボックス解除） | core / AWS | 🔲 |
| 7 | Market フロント実装（React Router 拡張 / 各画面 / API クライアント） | market | 🔲 |
| 8 | CloudFront Behavior 追加（`/api/customer/*`） | AWS | 🔲 |
| 9 | E2E（登録 → ログイン → マイページ → ログアウト → パスリセ） | 全体 | 🔲 |
| 10 | ドキュメント更新（README / トラブル記録 / phase11_20 進捗） | docs | 🔲 |

---

# 15. 採用しなかった選択肢

| 案 | 不採用理由 |
|---|----------|
| Redis（ElastiCache）でセッション共有 | 無料枠外（月 $12〜）、X-4 で確保したメモリ余裕も食う |
| DynamoDB TTL でセッション・トークン管理 | 無料枠はあるが運用基盤の分離コストとサービス追加リスク |
| Spring Session JDBC | 依存追加でコンテナメモリ増（X-4 の 384m ヒープ枠を圧迫）、自作で十分 |
| Spring Security CsrfFilter | Console（JWT）と Market（セッション）を同一 SecurityFilterChain で扱うとルール分岐が複雑になる |
| Console の `users` テーブル流用 | 社員番号 / ロールと顧客の属性が異なり、責務分離が崩れる |
| 郵便局 API の都度問い合わせ | 公式 API がない（HeartRails Geo API はサードパーティ依存）、自前 CSV 取込が確実 |
| ECS Scheduled Task / Lambda での住所マスタバッチ | 無料枠の運用分散コスト、Spring `@Scheduled` で十分 |
