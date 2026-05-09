# テーブル定義書：inquiry_messages

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | inquiry_messages |
| 論理名 | 問い合わせスレッドメッセージ |
| 所属システム | Core |
| 説明 | フェーズ18 問い合わせ管理のスレッド本体。1 件の `inquiries` レコードに紐づく顧客／管理者のメッセージを時系列で蓄積する。`is_internal_note` で管理者間共有メモを区別 |
| 追加フェーズ | フェーズ18（r3） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | メッセージID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | inquiry_id | 問い合わせID | BIGINT | - | NOT NULL | - | FK: inquiries.id（ON DELETE CASCADE） |
| 3 | sender_type | 送信者種別 | VARCHAR | 20 | NOT NULL | - | market_customer / admin_user。多態参照 |
| 4 | sender_id | 送信者ID | BIGINT | - | NOT NULL | - | `sender_type` に応じ market_customers.id / users.id（FK は張らない） |
| 5 | message | 本文 | TEXT | - | NOT NULL | - | 上限は `amazia.inquiry.message-max-length` で config 化 |
| 6 | is_internal_note | 内部メモフラグ | BOOLEAN | - | NOT NULL | FALSE | TRUE は admin_user のみ（CHECK で物理担保。Market API では DTO 分離で構造的に除外） |
| 7 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | `@PrePersist` で設定 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_inquiry_messages_inquiry_id_created_at | INDEX | (inquiry_id, created_at) |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| fk_inquiry_messages_inquiry | FK | `inquiry_id` → `inquiries.id` ON DELETE CASCADE |
| chk_inquiry_messages_sender_type | CHECK | `sender_type IN ('market_customer', 'admin_user')` |
| chk_inquiry_messages_internal_note_admin | CHECK | `is_internal_note = FALSE OR sender_type = 'admin_user'` |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| inquiries | N:1 | 親問い合わせ。CASCADE DELETE |
| market_customers | （sender_type='market_customer' のとき N:1 / FK なし） | 顧客発信のメッセージ |
| users | （sender_type='admin_user' のとき N:1 / FK なし） | 管理者発信のメッセージ |

## 設計上の注意

- 多態参照：`sender_type` / `sender_id` には FK を張らない（CHECK で値域のみ担保）。整合性は Service 層が責任を持つ。
- 内部メモの二重防御：DB CHECK（admin_user 限定）＋ Market 側 DTO（`MarketReplyInquiryRequest`）に `is_internal_note` フィールドを構造的に持たせない（RV-9 / Mass Assignment 攻撃対策）。
- Market 詳細 API は `findByInquiryIdAndIsInternalNoteFalseOrderByCreatedAtAsc` で内部メモを除外して返す。Console 詳細 API は `findByInquiryIdOrderByCreatedAtAsc` で全件返す。

## Entity

`com.example.inquiry.entity.InquiryMessage`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ18 Step 1-2）
