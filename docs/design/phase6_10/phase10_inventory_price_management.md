
# フェーズ10：在庫管理・価格管理・SKU対応・商品一覧改修

## ステータス
✅ 完了（2026-05-04）

### 実装済み内容
- **amazia-core**: SKU・価格・在庫エンティティ・API（TDD: JUnit 11件グリーン）
  - POST /api/products/{id}/skus（SKU登録・重複チェック）
  - GET /api/products/{id}/skus（SKU一覧）
  - POST /api/skus/{id}/prices（価格登録）
  - GET /api/skus/{id}/prices（現行価格取得）
  - POST /api/skus/{id}/stocks/receive（入荷・加算）
  - GET /api/skus/{id}/stocks（現在在庫）
  - GET /api/skus/{id}/stocks/history（入荷履歴）
- **amazia-console**: SKU管理API（TDD: PHPUnit 11件グリーン）
  - Core へのリクエストプロキシ・バリデーション実装
- **amazia-core**: SKU集約API（TDD: JUnit 5件グリーン）
  - GET /api/products/market（min_price・mainImage・totalStock）
  - GET /api/products/{id}/market（SKU詳細＋価格＋在庫＋画像）
  - 在庫0・SKUなし商品は一覧から除外
- **amazia-market**: SKU対応UI改修（ビルド確認済み）
  - 商品一覧：カード型・最低価格・SKUメイン画像・在庫表示
  - 商品詳細：色→サイズのSKU選択UI・価格/在庫チップ・画像サムネイル切り替え

## 範囲
- Amazia Console  
- Amazia Core  
- Amazia Market  
- DB設計（SKU前提）  
- 在庫・価格・SKU画像管理  

---

# 1. 機能概要
本フェーズでは、商品管理を **SKU（色 × サイズ）単位** に拡張し、  
価格・在庫・画像を SKU 単位で管理できるようにする。

主な改修内容：

- Console にサイドメニュー追加  
- 商品マスタと SKU マスタの分離  
- SKUごとの価格管理（現行価格＋未来価格＋履歴）  
- SKUごとの在庫管理（現在在庫＋入荷・調整履歴）  
- SKUごとの画像管理（複数画像・sort_order対応）  
- 商品一覧（Console / Market）の取得元を SKU 前提に変更  

---

# 2. Amazia Console

## 2.1 サイドメニュー追加
- 商品マスタ  
- SKU管理  
- SKU価格管理  
- SKU在庫管理  
- SKU画像管理  
- 商品一覧（SKU集約版）

---

# 3. 商品管理（Console）

## 3.1 商品マスタ（product）
商品そのものの情報を管理する。

### ■ 保持項目
- 商品名  
- 商品説明  
- カテゴリ  
- 公開開始日 / 公開終了日  
- ステータス（公開 / 非公開）  

### ■ 備考
- **商品マスタは価格・在庫を持たない**  
- SKUが1つも存在しない商品は Market に表示しない  

---

# 4. SKU管理（Console）

## 4.1 SKUマスタ（product_skus）
SKU（色 × サイズ）を管理する。

### ■ 保持項目
- product_id  
- sku_code（自動採番）  
- color（例：Red / Blue / Black）  
- size（例：S / M / L）  
- ステータス（販売中 / 停止）  

### ■ バリデーション
- 同一商品内で color + size の組み合わせはユニーク  
- SKU削除は不可（履歴保持のため）  
- 停止ステータスで Market 非表示  

---

# 5. SKU価格管理（Console）

## 5.1 現行価格（product_sku_prices）
SKUごとの現在の販売価格を保持する。

### ■ 保持項目
- sku_id  
- price  
- start_date  
- end_date（任意）  

---

## 5.2 価格履歴（product_sku_price_history）
過去・未来の価格を保持する。

### ■ 保持項目
- sku_id  
- price  
- start_date  
- end_date  
- status（past / future / applied）  

---

## 5.3 価格変更予約（未来価格）
- 未来の start_date を設定すると「future」として履歴に登録  
- 日次バッチで start_date を迎えたら：
  - 現行価格を上書き  
  - 履歴を applied に更新  

### ■ バリデーション
- start_date は現行価格の start_date より後  
- 商品公開終了日より後は不可  

---

# 6. SKU在庫管理（Console）

## 6.1 現在在庫（product_sku_stocks）
SKUごとの現在在庫を保持。

### ■ 保持項目
- sku_id  
- quantity  

---

## 6.2 在庫履歴（product_sku_stock_transactions）
入荷・調整などの履歴を保持。

### ■ 保持項目
- sku_id  
- type（入荷 / 調整）  
- quantity（増減値）  
- created_at  

---

## 6.3 Excelアップロード（入荷）
- 同じ sku_code が存在する場合 → quantity を加算  
- 存在しない sku_code → エラー  
- 減算は不可（入荷のみ）  

---

# 7. SKU画像管理（Console）

## 7.1 SKU画像（product_sku_images）
SKUごとの画像を複数管理。

### ■ 保持項目
- sku_id  
- image_path（S3パス）  
- sort_order（1がメイン）  

### ■ 仕様
- PNG固定  
- 200KB以下  
- 800px以内  
- 複数枚対応  
- sort_order変更可能  

---

# 8. 商品一覧（Console）

## 8.1 表示内容
- 商品名  
- SKU数  
- メイン画像（SKU画像の sort_order=1）  
- 最低価格（SKUの現行価格の最小値）  
- 在庫合計（SKU在庫の合計）  

---

# 9. Amazia Core（API）

## 9.1 商品一覧API（SKU集約）
返却内容：
- product  
- skus（color, size, price, stock）  
- main_image  
- min_price  
- total_stock  

---

## 9.2 商品詳細API
返却内容：
- product  
- skus（color, size, price, stock, images）  

---

## 9.3 SKU価格API
- 現行価格取得  
- 未来価格登録  
- 履歴取得  

---

## 9.4 SKU在庫API
- 現在在庫取得  
- 入荷登録  
- 調整登録  
- 履歴取得  

---

# 10. Amazia Market

## 10.1 商品一覧
- 商品単位で表示  
- SKUの最低価格を表示  
- SKUのメイン画像を表示  
- 在庫が1つもない商品は非表示  

---

## 10.2 商品詳細
- SKU選択UI（色 → サイズ）  
- SKUごとの価格表示  
- SKUごとの在庫表示  
- SKUごとの画像切り替え  

---

# 11. DB設計（SKUフル対応）

## 11.1 products  
（商品基本情報）

## 11.2 product_skus  
（色 × サイズ）

## 11.3 product_sku_prices  
（現行価格）

## 11.4 product_sku_price_history  
（価格履歴）

## 11.5 product_sku_stocks  
（現在在庫）

## 11.6 product_sku_stock_transactions  
（入荷・調整履歴）

## 11.7 product_sku_images  
（SKU画像）

---

# 12. TDDテストケース（要点）

## Console
- SKU登録（色 × サイズ）  
- SKU価格登録・未来価格予約  
- SKU在庫入荷・調整  
- SKU画像アップロード・sort_order変更  

## Core
- SKU集約商品一覧  
- SKU詳細  
- 価格バッチ処理  
- 在庫履歴反映  

## Market
- SKU選択UI  
- SKUごとの価格・在庫反映  
- SKU画像切り替え  
