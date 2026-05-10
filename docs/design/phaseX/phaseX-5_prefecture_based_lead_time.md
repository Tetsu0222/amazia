# フェーズX-5：都道府県別リードタイムマスタ化

## ステータス
✅ 完了（2026-05-10）

## 背景

[phase15_delivery_management.md](../phase11_20/phase15_delivery_management.md) r5 の §配送予定日の計算仕様（R-6 / RR-8）で、配送予定日のリードタイムは段階的整備の方針が示された：

| 段階 | 入力 |
|------|------|
| phase15 r5（実装済 / 2026-05-07） | `shipping_methods` × **全国一律のリードタイム**（`config/app/Delivery.php` / `application.properties`） |
| **phaseX-5（本書）** | `shipping_methods` × **都道府県別リードタイム**（`shipping_lead_times` マスタ） |

phase15 着手前のユーザー確認事項 #3 で「都道府県別リードタイムは将来課題（マスタ化）として切り出す」と合意済。本書ではその切り出し先設計を骨子レベルで定義する。

## スコープ

### 対象範囲
- Amazia Core: `shipping_lead_times` マスタテーブル新設、`DeliveryScheduleService.calculate(...)` に都道府県別リードタイム反映
- Amazia Console: マスタ管理画面（CRUD・簡易）
- DB 設計: 1 テーブル新設、配送予定日関連の既存仕様は変更なし

### スコープ外
- `shipping_lead_times` の海外配送対応（都道府県以外への配送）
- リードタイムの動的算出（祝日・繁忙期係数など）— 将来課題
- 入荷再計算ロジックの変更（既存 `DeliveryRescheduleService.recalculateForProduct` をそのまま流用）

## 機能概要

- 配送方法（`shipping_methods.id`）×都道府県（`address.prefecture`）の組合せでリードタイム（日数）を持つマスタを Core に新設
- `DeliveryScheduleService.calculate(Sales sales, int stockAvailable)` を拡張し、`sales.shipping_address_id` から `address.prefecture` を引き当て、`shipping_lead_times` を参照してリードタイムを取得する
- マスタ未登録の組合せはフォールバックとして既存 config（全国一律）の値を使う
- Console にマスタ管理画面（一覧 + 編集）を追加

## DB 設計（追加）

### shipping_lead_times テーブル（新規）

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| id | BIGINT | NOT NULL | PK |
| shipping_method_id | BIGINT | NOT NULL | `shipping_methods.id` への FK |
| prefecture | VARCHAR(20) | NOT NULL | 都道府県名（`address.prefecture` と一致する文字列） |
| lead_time_days | INT | NOT NULL | リードタイム日数（CHECK >= 0） |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

#### 制約
- `UNIQUE(shipping_method_id, prefecture)` で配送方法×都道府県の一意性を保証
- `CHECK (lead_time_days >= 0)`
- `FOREIGN KEY (shipping_method_id) REFERENCES shipping_methods(id)`

#### 初期データ
全 47 都道府県 × 3 配送方法（home_delivery / konbini_pickup / dropoff）= 141 行を schema.sql の INSERT IGNORE で投入する。phase15 r5 の全国一律値（home=3 / konbini=4 / dropoff=2）を初期値とし、東京都・大阪府・愛知県等の都市部はそのままで、北海道・沖縄県など離島系は +2 日加算する想定。

## 機能詳細

### Amazia Core

#### マスタ参照 API（追加）
- `GET /api/shipping-lead-times[?shippingMethodId=N]` — マスタ一覧
- `GET /api/shipping-lead-times/{id}` — 詳細

#### マスタ更新 API（追加）
- `PATCH /api/shipping-lead-times/{id}` — リードタイム更新
- `operation_logs` 記録対象（命名規約 §6 への追記が必要）

#### `DeliveryScheduleService.calculate(...)` の拡張
```
入力：Sales sales, int stockAvailable
1. address = addressRepository.findById(sales.shipping_address_id)
2. prefecture = address.prefecture（NULL の場合は config フォールバック）
3. leadTime = shippingLeadTimeRepository
       .findByShippingMethodIdAndPrefecture(sales.shipping_method_id, prefecture)
       .map(ShippingLeadTime::getLeadTimeDays)
       .orElseGet(() -> config('amazia.delivery.lead-time-days.<method>'))
4. return sales.salesDate.plusDays(leadTime)
```

#### 入荷再計算との関係
- `DeliveryRescheduleService.recalculateForProduct` は `DeliveryScheduleService.calculate` を呼ぶだけなので、リードタイムが都道府県別になっても変更不要
- 各 `delivery` の都道府県別リードタイムが自動的に再計算に反映される

### Amazia Console

#### マスタ管理画面（追加）
- ルート: `/shipping-lead-times`
- 画面: `ShippingLeadTimeList.vue`（47×3 のグリッド or テーブル）
- 編集: 行クリックでインライン編集 or ダイアログ
- ロール: `approver_roles`（supervisor 以上）

#### Console 中継 API
- `GET /api/shipping-lead-times[?shippingMethodId=N]`
- `PATCH /api/shipping-lead-times/{id}`

### Amazia Market

変更なし。配送予定日の表示は phase15 r5 で実装済の `delivery.scheduledDate` をそのまま使う（Core 側の `DeliveryScheduleService.calculate` が拡張されるため、Market 側ロジック修正不要）。

## TDDテストケース

### Amazia Core / JUnit
- `shipping_lead_times` マスタの初期データが 47×3 = 141 件投入されている
- `DeliveryScheduleService.calculate` で都道府県別リードタイムが反映される（東京 home_delivery=3日 / 北海道 home_delivery=5日 等）
- マスタ未登録の都道府県は config フォールバックを使う
- `address.prefecture = null` の場合も config フォールバックで算出可能
- 入荷再計算でも都道府県別リードタイムが反映される
- マスタ更新後の `operation_logs` 記録（action=`update_shipping_lead_time`）

### Amazia Console / PHPUnit
- マスタ一覧 GET の中継
- マスタ更新 PATCH の認可（user ロールは 403）
- 0 / 負数リードタイムでは 422

## 設計上の注意

- 都道府県名の文字列正規化：`address.prefecture` は VARCHAR(50) で自由入力されているため、マスタの `prefecture` と一致しないケースがある。マッチングは厳密一致のみで、不一致時は config フォールバック（防御的設計）
- パフォーマンス：マスタは 141 行で固定。`@Cacheable` を使うほどでもないが、Service 内で初回ロード時に `Map<Long, Map<String, Integer>>` にキャッシュする実装も検討
- マスタ削除：UI 側では「リードタイム=0 で無効化」運用とし、物理削除は許容しない（phase15 と同じくマスタは shipping_methods と shipping_lead_times の両方に対する FK 整合性を保つ）

## 共通命名規約への追加

`docs/ai_context/operation_logs_naming.md` §6 への追記提案：

| 操作 | action | screen_name | api_name |
|------|--------|-------------|----------|
| リードタイム更新 | `update_shipping_lead_time` | `console.shipping_lead_time.update` | `PATCH /api/shipping-lead-times/{id}` |

## phase15 / phase14 r2 との関係

- phase15 r5 で導入した `DeliveryScheduleService` の構造は変えない（リードタイム取得元のみ拡張）
- phase14 r2 で予定の `products.stock` 廃止 / `inventories` 完全移行とは独立。同時実装/分離実装どちらでも可
- 本フェーズ完了後、phase15 r5 の §技術検討事項「都道府県別リードタイム」項目を解消マークする

## 参考リンク

- 元設計書: [phase15_delivery_management.md](../phase11_20/phase15_delivery_management.md) §配送予定日の計算仕様
- phase15 r5 で確定した過渡的設計: `application.properties > amazia.delivery.lead-time-days.*`
- 命名規約: [operation_logs_naming.md](../../ai_context/operation_logs_naming.md)
