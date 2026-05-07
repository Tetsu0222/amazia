# operation_logs 命名規約

`operation_logs` テーブルの `screen_name` / `api_name` カラムに格納する具体値の命名規約を定義する。phase14 r4 / phase15 r4 共通。

新規 Controller / Service / 画面を追加する際は、本書の命名規則に従って値を決定し、Service 層で `operation_logs` レコードを記録する。

---

## 1. 共通方針

- **小文字スネークケース**（lower_snake_case）
- **ドメイン名から始める**（`console.` / `market.` / `core.`）
- **階層はドット区切り**で、第2層に画面 or リソース、第3層に操作種別を置く
- **動詞は原則使わない**（`action` カラムが動詞を担うため、`screen_name` / `api_name` は「場所」を示す名詞中心）
- **ID 等の動的部分は含めない**（パスパラメータは `:id` のように記号化、または含めない）

---

## 2. screen_name の命名規則

画面（UI）からのトリガーで発火した操作に付与する名前。Console / Market / その他の発火元を区別できるよう、最上位はアプリ名から始める。

### 構文
```
{app}.{domain}.{screen}
```

- `{app}`: `console` / `market` / `core`（バッチ系で UI を持たない場合は `core.batch` 等）
- `{domain}`: ドメイン名（`sales` / `delivery` / `product` / `inventory` 等）。`docs/coding_guidelines.md` のフォルダ第1層と一致させる
- `{screen}`: 画面名。一覧 / 詳細 / 編集 / 登録 を `list` / `detail` / `edit` / `create` で表す慣例

### 例

| screen_name | 説明 |
|-------------|------|
| `console.sales.list` | Console 売上管理画面（一覧） |
| `console.sales.detail` | Console 売上詳細画面 |
| `console.delivery.list` | Console 配送管理画面（一覧） |
| `console.delivery.update_status` | Console 配送ステータス更新画面 |
| `console.delivery.update_address` | Console 配送先変更画面 |
| `console.delivery.update_scheduled_date` | Console 配送予定日変更画面 |
| `console.delivery.register_tracking` | Console 追跡番号登録画面 |
| `console.inbound.register` | Console 入荷登録画面 |
| `console.sales_return.list` | Console 返品管理画面（一覧） |
| `console.sales_return.approve` | Console 返品承認画面 |
| `console.operation_log.list` | Console 操作履歴一覧画面 |
| `console.product.list` | Console 商品一覧 |
| `console.product.create` | Console 商品登録 |
| `market.product.list` | Market 商品一覧 |
| `market.product.detail` | Market 商品詳細 |
| `market.checkout.confirm` | Market チェックアウト確認画面 |
| `market.checkout.complete` | Market 注文完了画面 |
| `market.purchase_history.list` | Market 購入履歴 |
| `market.customer.register` | Market 会員登録画面 |
| `market.customer.mypage` | Market マイページ |

### バッチ・非UI起因の操作

UI を介さず Service / バッチから直接発火した操作は、`screen_name` を NULL にせず以下の規則に従う：

| screen_name | 用途 |
|-------------|------|
| `core.batch.{ジョブ名}` | バッチ処理（例: `core.batch.inbound_recalc`） |
| `core.scheduled.{タスク名}` | スケジュール起動タスク |
| `core.system.{処理名}` | システム内部処理（例: 自動キャンセル等） |

---

## 3. api_name の命名規則

API エンドポイント由来で発火した操作に付与する名前。HTTP メソッドとパスを組み合わせる。

### 構文
```
{HTTP_METHOD} {PATH}
```

- `{HTTP_METHOD}`: 大文字（GET / POST / PUT / PATCH / DELETE）
- `{PATH}`: API パス。パスパラメータは `:id` 等で記号化（具体値を入れない）
- パスは API ルート（`/api`）から記述

### 例

| api_name | 説明 |
|----------|------|
| `POST /api/orders/confirm` | 注文確定 API |
| `GET /api/sales` | 売上一覧取得 |
| `PATCH /api/deliveries/:id/status` | 配送ステータス更新 |
| `PATCH /api/deliveries/:id/address` | 配送先変更 |
| `PATCH /api/deliveries/:id/scheduled-date` | 配送予定日変更 |
| `PATCH /api/deliveries/:id/tracking-code` | 追跡番号登録 |
| `POST /api/inbounds` | 入荷登録 |
| `POST /api/customer/sales-returns` | 返品申請（Market 起点） |
| `POST /api/sales-returns/:id/approve` | 返品承認 |
| `POST /api/sales-returns/:id/reject` | 返品却下 |
| `POST /api/sales-returns/:id/refund` | 返金完了 |
| `POST /api/customers/register` | 会員登録 |
| `POST /api/customers/login` | ログイン |

### 変則ケース

- 同一 Controller で複数の操作を行う場合（PATCH で複数フィールド更新等）は、`screen_name` 側で識別を担保する
- gRPC 等 HTTP 以外のプロトコルは別途規約を追加（本フェーズではスコープ外）

---

## 4. action カラムとの関係

`operation_logs` テーブルには `action` / `screen_name` / `api_name` の3カラムがあり、それぞれ役割が異なる：

| カラム | 役割 | 例 |
|--------|------|-----|
| `action` | **動詞 + 対象**。何をしたか | `update_shipping_status` / `register_inbound` / `approve_sales_return` |
| `screen_name` | **発火元の画面（場所）** | `console.delivery.update_status` |
| `api_name` | **発火元の API エンドポイント** | `PATCH /api/deliveries/:id/status` |

3カラム揃うことで「**いつ誰が、どの画面の、どの API を叩いて、何をしたか**」が一意に追跡できる。

### action の命名規則

- 小文字スネークケース
- **動詞 + 名詞** の形式
- 名詞は単数形

#### よく使う action 例

| action | 説明 |
|--------|------|
| `create_*` | 新規作成（例: `create_inbound`） |
| `update_*` | 更新（例: `update_shipping_status`, `update_shipping_address`, `update_scheduled_date`, `update_tracking_code`） |
| `delete_*` | 削除（例: `delete_product`） |
| `approve_*` | 承認（例: `approve_sales_return`） |
| `reject_*` | 却下（例: `reject_sales_return`） |
| `refund_*` | 返金（例: `refund_sales_return`） |
| `register_*` | 登録（例: `register_inbound`, `register_tracking_code`） |
| `cancel_*` | キャンセル（例: `cancel_sales`） |
| `confirm_*` | 確定（例: `confirm_order`） |

---

## 5. comment カラムの規約

`operation_logs.comment` には自由テキストを格納できるが、集計可能性のために以下のプレフィックス規約を導入する（phase15 r4 起因）：

| プレフィックス | 用途 |
|---------------|------|
| `[manual]` | 管理者の手動操作（例: 配送予定日の手動修正） |
| `[inbound_recalc]` | 入荷登録による配送予定日再計算 |
| `[shipping_delay]` | 出荷遅延による配送予定日変更 |
| （プレフィックスなし） | 上記に該当しない一般コメント |

プレフィックス値は `config/app/Delivery.php`（PHP）/ `application.yml > amazia.delivery.scheduled_date_reasons`（Java）で enum 定義し、Service 層で生成時に固定する。

将来的に集計需要が高まった段階で `operation_logs.reason_code` カラムを追加することを検討（phase14 r4 で将来課題に登録済み）。

---

## 6. 採番例（phase14 r4 + phase15 r4 想定）

phase14 / phase15 で利用される値の一覧（実装時のリファレンス）：

### Console 起点

| 操作 | action | screen_name | api_name |
|------|--------|-------------|----------|
| 配送ステータス更新 | `update_shipping_status` | `console.delivery.update_status` | `PATCH /api/deliveries/:id/status` |
| 配送先変更 | `update_shipping_address` | `console.delivery.update_address` | `PATCH /api/deliveries/:id/address` |
| 配送予定日変更 | `update_scheduled_date` | `console.delivery.update_scheduled_date` | `PATCH /api/deliveries/:id/scheduled-date` |
| 追跡番号登録 | `register_tracking_code` | `console.delivery.register_tracking` | `PATCH /api/deliveries/:id/tracking-code` |
| 入荷登録 | `register_inbound` | `console.inbound.register` | `POST /api/inbounds` |
| 出荷時在庫不足（自動記録） | `shipping_blocked_insufficient_stock` | `console.delivery.update_status` | `PATCH /api/deliveries/:id/status` |
| 返品承認 | `approve_sales_return` | `console.sales_return.approve` | `POST /api/sales-returns/:id/approve` |
| 返品却下 | `reject_sales_return` | `console.sales_return.approve` | `POST /api/sales-returns/:id/reject` |
| 返金完了 | `refund_sales_return` | `console.sales_return.approve` | `POST /api/sales-returns/:id/refund` |

### Market 起点

> **記録対象外（2026-05-06 確定）**：`operation_logs.user_id` は **`users.id`（管理者）参照の FK** を持つため、`market_customers` 由来の操作は記録できない。Market 起点の操作（注文確定・返品申請等）は本テーブルに記録**しない**。会員側の操作履歴は `sales` / `sales_return` テーブル自体の更新時刻と外部キーで追跡する設計。

| 操作 | action | screen_name | api_name |
|------|--------|-------------|----------|
| ~~注文確定~~ | （記録対象外） | — | — |
| ~~返品申請~~ | （記録対象外） | — | — |

### バッチ起点

| 操作 | action | screen_name | api_name |
|------|--------|-------------|----------|
| 入荷再計算（自動） | `update_scheduled_date` | `core.batch.inbound_recalc` | NULL |

---

## 7. 改訂履歴

| 版 | 日付 | 内容 |
|----|------|------|
| 初版 | 2026-05-06 | phase14 r4 / phase15 r4 共通規約として新規作成 |
| r2 | 2026-05-07 | phase15 r5 実装完了に伴い `shipping_blocked_insufficient_stock` を §6 採番例に追加（在庫不足で SHIPPED 遷移できなかった場合の自動記録 / P5-4。Controller が `REQUIRES_NEW` で別 TX 記録するため、外側のロールバックの影響を受けない） |
