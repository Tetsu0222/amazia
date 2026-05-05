
# 📘 フェーズ11：Amazia Console ログイン・認証/認可機能

## ステータス
🔲 未着手

## 対象範囲
- Amazia Console（フロントエンド）
- Amazia Core（API）
- 認証・認可機能
- DB 設計
- メール送信基盤（パスワード再発行）

---

# 1. 機能概要

Amazia Console に以下の認証・認可機能を実装する。

- HTTPS 化（ALB + ACM）
- ログイン機能（JWT 認証）
- パスワード再発行機能（メール送信）
- 社員登録（ユーザー管理）
- ロール管理（管理者 / 一般）
- パーミッション管理（画面単位）
- Admin アカウントの初期投入

---

# 2. 認証方式

## 2.1 認証方式
- **JWT（JSON Web Token）方式** を採用  
- アクセストークン + リフレッシュトークンの 2 種類を使用

### アクセストークン
- 有効期限：**15分**
- 署名方式：HS256
- 保存場所：ブラウザの **メモリ（Vuex/Pinia）**

### リフレッシュトークン
- 有効期限：**14日**
- 保存場所：**HttpOnly Cookie**
- Secure / SameSite=Lax を付与

---

# 3. HTTPS 化

## 3.1 対応内容
- ALB に ACM 証明書を設定し HTTPS 化
- HTTP → HTTPS へリダイレクトを強制
- 内部通信（ALB → ECS）は HTTP のままで OK

## 3.2 ドメイン
- Console：`https://console.amazia.example.com`
- API：`https://api.amazia.example.com`

---

# 4. ログイン機能

## 4.1 ログインID
- **メールアドレス** をログインIDとして使用  
- 社員IDは内部管理用でログインには使用しない

## 4.2 ログインフロー
1. メールアドレス + パスワード入力  
2. Core API に認証リクエスト  
3. 認証成功 → アクセストークン + リフレッシュトークン発行  
4. Console に遷移

## 4.3 アカウント状態
- `active_flag`（boolean）を持つ  
- false の場合ログイン不可

## 4.4 ロックアウト
- 連続 5 回失敗でロック  
- 15 分後に自動解除

---

# 5. パスワード再発行

## 5.1 メール送信基盤
- AWS SES を使用  
- 送信元：`no-reply@amazia.example.com`

## 5.2 再発行フロー
1. メールアドレス入力  
2. 登録情報と一致確認  
3. 再発行用トークンを生成  
4. トークンを DB に保存（有効期限：**30分**）  
5. 再発行URLをメール送信  
6. 新パスワード入力画面へ遷移  
7. パスワード更新

## 5.3 トークン仕様
- ランダム 64 文字  
- DB にハッシュ化して保存  
- 1 回限り有効

---

# 6. 社員登録機能

## 6.1 入力項目
| 項目 | 必須 | 備考 |
|------|------|------|
| 社員ID | 必須 | 内部管理用ユニークID |
| メールアドレス | 必須 | ログインIDとして使用 |
| 名前 | 必須 | 50文字以内 |
| パスワード | 必須 | バリデーションあり |
| ロール | 必須 | 管理者 / 一般 |

## 6.2 パスワードポリシー
- 8文字以上  
- 英大文字・英小文字・数字を含む  
- 過去 3 回分のパスワードは再利用不可  
- ハッシュ方式：**BCrypt**

---

# 7. ロール・パーミッション

## 7.1 ロール
- 管理者（admin）
- 一般（user）

## 7.2 パーミッション粒度
- **画面単位（URL単位）**  
- ボタン単位の制御は行わない

## 7.3 DB モデル
- `roles`  
- `permissions`  
- `role_permissions`（中間テーブル）

## 7.4 認可方式
- Console：メニュー表示制御  
- API：エンドポイント単位でチェック

---

# 8. Admin アカウント初期投入

## 8.1 方式
- **DB マイグレーションで投入**

## 8.2 初期値
| 項目 | 値 |
|------|------|
| メール | admin@amazia.example.com |
| パスワード | システム生成（ランダム） |
| ロール | 管理者 |
| 権限 | 全パーミッション |

## 8.3 初期パスワード通知
- 運用担当へ別途連絡（Slack or 手渡し）

---

# 9. セッションタイムアウト

## 9.1 Console 側
- 15分操作なし → 自動ログアウト  
- トークン期限切れ時もログアウト

## 9.2 API 側
- アクセストークン期限切れ → 401  
- リフレッシュトークンで再発行  
- リフレッシュも期限切れ → 再ログイン

---

# 10. 画面仕様

## 10.1 ログイン画面
- メールアドレス  
- パスワード  
- ログインボタン  
- パスワード再発行リンク

## 10.2 パスワード再発行画面
- メールアドレス入力  
- 再発行メール送信ボタン

## 10.3 パスワード再設定画面
- 新パスワード  
- 新パスワード（確認）

## 10.4 社員登録画面
- 社員ID  
- メールアドレス  
- 名前  
- パスワード  
- ロール選択

## 10.5 社員一覧画面
- 社員ID  
- メールアドレス  
- 名前  
- ロール  
- 有効/無効  
- 編集ボタン

## 10.6 社員編集画面
- メールアドレス  
- 名前  
- ロール  
- 有効/無効  
- パスワードリセット

---

# 11. DB 設計（主要テーブル）

## 11.1 users
| カラム | 型 | 備考 |
|--------|------|------|
| id | bigint | PK |
| employee_id | varchar | 社員ID |
| email | varchar | ログインID |
| name | varchar | |
| password_hash | varchar | BCrypt |
| role_id | bigint | FK |
| active_flag | boolean | |
| failed_attempts | int | ロックアウト用 |
| created_at | datetime | |
| updated_at | datetime | |

## 11.2 roles
| カラム | 型 |
|--------|------|
| id | bigint |
| code | varchar |
| name | varchar |

## 11.3 permissions
| カラム | 型 |
|--------|------|
| id | bigint |
| screen_id | varchar |
| name | varchar |

## 11.4 role_permissions
| カラム | 型 |
|--------|------|
| role_id | bigint |
| permission_id | bigint |

## 11.5 password_reset_tokens
| カラム | 型 |
|--------|------|
| id | bigint |
| user_id | bigint |
| token_hash | varchar |
| expires_at | datetime |

---

# 12. API 一覧（主要）

## POST /auth/login
ログイン

## POST /auth/refresh
トークン再発行

## POST /auth/password/reset/request
再発行メール送信

## POST /auth/password/reset/confirm
パスワード更新

## GET /users
社員一覧

## POST /users
社員登録

## PUT /users/{id}
社員編集

---

# 13. セキュリティ要件

- パスワードは平文保存禁止  
- JWT の秘密鍵は Parameter Store で管理  
- API は全て HTTPS  
- CORS 設定は Console のみ許可  
- HttpOnly Cookie を使用  
- XSS/CSRF 対策を実施

---

# 14. リスクと対策

| リスク | 対策 |
|--------|------|
| Admin アカウント紛失 | 初期パスワードを安全に保管 |
| トークン漏洩 | 有効期限短縮 + HttpOnly |
| 権限設定ミス | 初期ロール定義を固定化 |
| メール送信失敗 | SES のバウンス監視 |

---

# 15. 付録：画面遷移図（簡易）

```
[ログイン] → [ダッシュボード]
     ↓
[パスワード再発行]
     ↓
[再設定画面]

[社員一覧] → [社員登録]
           → [社員編集]
```

---

# 16. 実装計画

## 16.1 実装方針

- TDD（テスト先行）で進める
- コーディング規約（`docs/coding_guidelines.md`）に従い、ユースケース単位でフォルダ・ファイルを切る
- Console（PHP/Laravel）と Core（Java/Spring）を並行して実装する
- ステップ単位で動作確認しながら進める

---

## 16.2 ステップ一覧

| # | ステップ | 対象 | 内容 |
|---|---------|------|------|
| 1 | DB マイグレーション | Core (MySQL) | users / roles / permissions / role_permissions / password_reset_tokens の作成 + Admin 初期データ投入 |
| 2 | ログイン API | Core (Spring) | `POST /auth/login`：認証 + JWT（アクセス/リフレッシュ）発行 |
| 3 | トークン再発行 API | Core (Spring) | `POST /auth/refresh`：リフレッシュトークン検証 + 再発行 |
| 4 | 認証ミドルウェア | Console (Laravel) | JWT 検証 Middleware + ルートガード |
| 5 | 社員 CRUD API | Core (Spring) + Console (Laravel) | `GET/POST /users`、`PUT /users/{id}` |
| 6 | パスワード再発行 API | Core (Spring) | `POST /auth/password/reset/request`・`/confirm` + SES メール送信 |
| 7 | Console Vue.js 画面 | Console (Vue) | ログイン・パスワード再発行・社員一覧/登録/編集 画面 |
| 8 | ロール・パーミッション制御 | Console (Vue + Laravel) | メニュー表示制御 + API エンドポイント認可チェック |
| 9 | HTTPS 化 | AWS (ALB + ACM) | 証明書設定・HTTP→HTTPS リダイレクト |
| 10 | 結合確認・セキュリティチェック | 全体 | E2E 動作確認・CORS/CSRF/XSS レビュー |

---

## 16.3 ステップ詳細

### ステップ 1：DB マイグレーション（Core / MySQL）

**目的：** 認証・認可に必要なテーブルを作成し、Admin 初期データを投入する。

**作成テーブル：**
- `roles`
- `permissions`
- `role_permissions`
- `users`（`failed_attempts`・`locked_until` カラムを含む）
- `password_reset_tokens`

**初期データ：**
- roles：admin / user の 2 件
- permissions：画面単位（ログイン以外の全画面）
- role_permissions：admin に全権限付与
- users：admin アカウント 1 件（パスワードはランダム生成、BCrypt ハッシュ）

**ファイル配置（Core）：**
```
src/main/resources/db/migration/
  V1__create_auth_tables.sql
  V2__insert_initial_data.sql
```

**テスト：**
- マイグレーション後のテーブル存在確認（H2 で実行可能な形式）

---

### ステップ 2：ログイン API（Core / Spring）

**エンドポイント：** `POST /auth/login`

**ファイル配置：**
```
com.example.auth/
  controller/LoginController.java
  service/LoginService.java
  entity/User.java
  entity/Role.java
  repository/UserRepository.java
```

**処理フロー：**
1. メール + パスワードで UserRepository から検索
2. `active_flag = false` → 403
3. `locked_until` が現在時刻より未来 → 423（ロックアウト）
4. BCrypt でパスワード照合失敗 → `failed_attempts` インクリメント（5回でロック）
5. 成功 → JWT アクセストークン（15分）+ リフレッシュトークン（14日）発行
6. リフレッシュトークンは DB に保存、レスポンスは HttpOnly Cookie にセット

**JWT 秘密鍵：** `@Value` で AWS Parameter Store（環境変数経由）から取得

**テスト（TDD）：**
- 正常ログイン
- メール不一致 → 401
- パスワード不一致 → 401
- ロックアウト中 → 423
- active_flag = false → 403
- 5回失敗でロック確認

---

### ステップ 3：トークン再発行 API（Core / Spring）

**エンドポイント：** `POST /auth/refresh`

**ファイル配置：**
```
com.example.auth/
  controller/RefreshTokenController.java
  service/RefreshTokenService.java
  entity/RefreshToken.java
  repository/RefreshTokenRepository.java
```

**処理フロー：**
1. Cookie からリフレッシュトークン取得
2. DB の `refresh_tokens` で検証（有効期限・失効フラグ）
3. 旧トークンを失効にして新アクセストークン発行
4. リフレッシュトークンのローテーション（任意）

**テスト：**
- 正常再発行
- トークン期限切れ → 401
- 存在しないトークン → 401

---

### ステップ 4：認証ミドルウェア（Console / Laravel）

**目的：** Console の API ルートに JWT 検証ガードを追加する。

**ファイル配置：**
```
app/Shared/Middleware/
  AuthenticateJwt.php      ← Bearer トークンを検証し、ユーザー情報を request にセット
app/Shared/Service/
  JwtVerifyService.php     ← Core の公開鍵 or 共有シークレットで検証
config/app/Auth.php        ← JWT_SECRET などの設定
```

**ルート適用：**
```php
// routes/api.php
Route::middleware('auth.jwt')->group(function () {
    require __DIR__.'/api/Product.php';
    require __DIR__.'/api/Sales.php';
    // ...
});
```

**テスト：**
- 有効トークンで 200
- トークンなし → 401
- 期限切れトークン → 401

---

### ステップ 5：社員 CRUD API（Core + Console）

**Core エンドポイント：**

| メソッド | パス | 内容 |
|---------|------|------|
| GET | `/users` | 社員一覧 |
| POST | `/users` | 社員登録 |
| PUT | `/users/{id}` | 社員編集（名前・メール・ロール・active_flag） |

**Core ファイル配置：**
```
com.example.user/
  controller/ListUserController.java
  controller/CreateUserController.java
  controller/UpdateUserController.java
  service/ListUserService.java
  service/CreateUserService.java
  service/UpdateUserService.java
  entity/User.java           ← ステップ2と共有
  repository/UserRepository.java ← ステップ2と共有
```

**Console ファイル配置：**
```
app/User/
  Controller/ListUserController.php
  Controller/CreateUserController.php
  Controller/UpdateUserController.php
  Service/ListUserService.php
  Service/CreateUserService.php
  Service/UpdateUserService.php

routes/api/User.php

config/app/User.php          ← バリデーションルール（パスワードポリシー等）
```

**テスト：**
- 一覧取得（正常・認証なし→401）
- 登録（正常・バリデーションエラー・メール重複）
- 編集（正常・存在しないID→404）

---

### ステップ 6：パスワード再発行 API（Core + SES）

**エンドポイント：**
- `POST /auth/password/reset/request`
- `POST /auth/password/reset/confirm`

**Core ファイル配置：**
```
com.example.auth/
  controller/PasswordResetRequestController.java
  controller/PasswordResetConfirmController.java
  service/PasswordResetRequestService.java
  service/PasswordResetConfirmService.java
  entity/PasswordResetToken.java
  repository/PasswordResetTokenRepository.java
```

**SES 設定：**
- `application.yml` に `aws.ses.from-address` を定義
- `@ConfigurationProperties` で注入
- 送信先は登録メールアドレスのみ（外部への誤送信防止）

**テスト：**
- request：登録メールあり → 200 + トークン生成確認
- request：未登録メール → 200（列挙攻撃対策でエラーを返さない）
- confirm：正常 → パスワード更新
- confirm：期限切れトークン → 400
- confirm：使用済みトークン → 400

---

### ステップ 7：Console Vue.js 画面

**ファイル配置：**
```
resources/vue/features/
  Auth/
    pages/LoginPage.vue
    pages/PasswordResetRequestPage.vue
    pages/PasswordResetConfirmPage.vue
    api/authApi.js
  User/
    pages/ListUserPage.vue
    pages/CreateUserPage.vue
    pages/EditUserPage.vue
    api/userApi.js
```

**Pinia ストア：**
```
resources/vue/stores/
  authStore.js     ← アクセストークン保持・ログイン状態管理
```

**ルーター（Vue Router）：**
- `/login` → LoginPage（認証不要）
- `/password/reset` → PasswordResetRequestPage（認証不要）
- `/password/reset/confirm` → PasswordResetConfirmPage（認証不要）
- `/users` → ListUserPage（要認証 + admin ロール）
- `/users/new` → CreateUserPage（要認証 + admin ロール）
- `/users/:id/edit` → EditUserPage（要認証 + admin ロール）

**トークン自動リフレッシュ：**
- axios インターセプターで 401 を検知 → `/auth/refresh` を呼び再試行

---

### ステップ 8：ロール・パーミッション制御

**Console（Vue側）：**
- authStore にロール情報を保持
- Vue Router の `beforeEach` でアクセス権チェック
- 権限のない画面へのアクセス → 403 ページにリダイレクト

**Console（Laravel側）：**
- `AuthenticateJwt` Middleware でロール情報を request に付加
- ロール・パーミッション検証ミドルウェアを追加

```
app/Shared/Middleware/
  AuthenticateJwt.php
  CheckPermission.php      ← screen_id に対するパーミッションチェック
```

**設定：**
```php
// config/app/Auth.php
'role_permissions' => [
    'admin' => ['*'],        // 全画面
    'user'  => ['product.*', 'sales.*'],
],
```

---

### ステップ 9：HTTPS 化（AWS ALB + ACM）

**作業内容：**
1. ACM で証明書リクエスト（`*.amazia.example.com`）
2. ALB リスナーに HTTPS（443）を追加、証明書を紐付け
3. HTTP（80）リスナーに HTTP→HTTPS リダイレクトルールを設定
4. CORS 設定で Console オリジンのみ許可（Core の `WebConfig.java` 更新）
5. Cookie の `Secure` フラグを有効化

**確認ポイント：**
- `https://console.amazia.example.com` でアクセス可能
- `http://` アクセスが `https://` にリダイレクトされる
- API 疎通確認

---

### ステップ 10：結合確認・セキュリティチェック

**E2E 確認シナリオ：**
1. ログイン → ダッシュボード遷移
2. 15分無操作 → 自動ログアウト
3. トークン期限切れ → 自動リフレッシュ → 継続操作
4. 連続5回失敗 → ロック → 15分後解除
5. パスワード再発行 → メール受信 → 再設定
6. admin で社員登録 → 一般ユーザーでログイン → 社員管理画面にアクセス不可

**セキュリティレビューチェックリスト：**
- [ ] パスワードが平文でログ出力されていない
- [ ] JWT 秘密鍵が Parameter Store から取得されている
- [ ] HttpOnly Cookie が設定されている
- [ ] CORS が Console オリジンのみ許可されている
- [ ] SQL インジェクション対策（JPA/Eloquent のバインド使用）
- [ ] XSS 対策（Vue のテンプレートエスケープ）

---

## 16.4 新規追加ファイル一覧（予定）

### amazia-core（Java/Spring）

```
com.example.auth/
  controller/LoginController.java
  controller/RefreshTokenController.java
  controller/PasswordResetRequestController.java
  controller/PasswordResetConfirmController.java
  service/LoginService.java
  service/RefreshTokenService.java
  service/PasswordResetRequestService.java
  service/PasswordResetConfirmService.java
  entity/User.java
  entity/Role.java
  entity/Permission.java
  entity/RolePermission.java
  entity/RefreshToken.java
  entity/PasswordResetToken.java
  repository/UserRepository.java
  repository/RefreshTokenRepository.java
  repository/PasswordResetTokenRepository.java
com.example.user/
  controller/ListUserController.java
  controller/CreateUserController.java
  controller/UpdateUserController.java
  service/ListUserService.java
  service/CreateUserService.java
  service/UpdateUserService.java
```

### amazia-console（PHP/Laravel）

```
app/Auth/
  Controller/LoginController.php
  Controller/RefreshTokenController.php
  Controller/PasswordResetController.php
  Service/LoginService.php
  Service/RefreshTokenService.php
  Service/PasswordResetService.php
app/User/
  Controller/ListUserController.php
  Controller/CreateUserController.php
  Controller/UpdateUserController.php
  Service/ListUserService.php
  Service/CreateUserService.php
  Service/UpdateUserService.php
app/Shared/Middleware/
  AuthenticateJwt.php
  CheckPermission.php
app/Shared/Service/
  JwtVerifyService.php
config/app/Auth.php
config/app/User.php
routes/api/Auth.php
routes/api/User.php
```

### amazia-console（Vue.js）

```
resources/vue/
  stores/authStore.js
  features/Auth/
    pages/LoginPage.vue
    pages/PasswordResetRequestPage.vue
    pages/PasswordResetConfirmPage.vue
    api/authApi.js
  features/User/
    pages/ListUserPage.vue
    pages/CreateUserPage.vue
    pages/EditUserPage.vue
    api/userApi.js
```

---

## 16.5 環境変数・設定追加（予定）

新規環境変数を追加する際は `docker-compose.yml`・`phpunit.xml`・`application-test.properties` をセットで更新すること。

| 変数名 | サービス | 用途 |
|--------|---------|------|
| `JWT_SECRET` | Core / Console | JWT 署名シークレット（Parameter Store から注入） |
| `JWT_ACCESS_TTL` | Core | アクセストークン有効期限（秒） |
| `JWT_REFRESH_TTL` | Core | リフレッシュトークン有効期限（秒） |
| `AWS_SES_FROM_ADDRESS` | Core | SES 送信元メールアドレス |
| `PASSWORD_RESET_URL` | Core | 再発行URL のベース（Console の URL） |
