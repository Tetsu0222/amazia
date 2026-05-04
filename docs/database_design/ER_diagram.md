# ER図

## システム：Console

```mermaid
erDiagram
    users {
        BIGINT_UNSIGNED id PK
        VARCHAR name
        VARCHAR email UK
        TIMESTAMP email_verified_at
        VARCHAR password
        VARCHAR remember_token
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    password_reset_tokens {
        VARCHAR email PK
        VARCHAR token
        TIMESTAMP created_at
    }

    sessions {
        VARCHAR id PK
        BIGINT_UNSIGNED user_id FK
        VARCHAR ip_address
        TEXT user_agent
        LONGTEXT payload
        INT last_activity
    }

    personal_access_tokens {
        BIGINT_UNSIGNED id PK
        VARCHAR tokenable_type
        BIGINT_UNSIGNED tokenable_id
        TEXT name
        VARCHAR token UK
        TEXT abilities
        TIMESTAMP last_used_at
        TIMESTAMP expires_at
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    users ||--o{ sessions : "1:N"
    users ||--o{ personal_access_tokens : "1:N (Polymorphic)"
    users ||--o| password_reset_tokens : "1:0..1"
```

## テーブル一覧

| テーブル名 | 論理名 | 用途 |
|------------|--------|------|
| users | ユーザー | Console管理者のアカウント情報 |
| password_reset_tokens | パスワードリセットトークン | パスワード再設定フロー |
| sessions | セッション | Webセッション管理 |
| personal_access_tokens | パーソナルアクセストークン | Sanctum APIトークン認証 |

## 備考

- 現フェーズ（フェーズ11〜13）で認証機能を実装予定。商品・在庫・受注等のドメインテーブルは各フェーズで追加される。
- `personal_access_tokens.tokenable` はPolymorphic関連のため、将来的に複数モデル（Consoleユーザー・Marketユーザー等）に対応可能。
