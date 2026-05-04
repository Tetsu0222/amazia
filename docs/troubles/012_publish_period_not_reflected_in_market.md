# 012: Console で公開期間を設定しても Market に商品が表示されない

## ステータス
✅ 解決済（2026-05-04）

## 発症箇所
Amazia Console → 商品編集 → 公開開始日時を「過去日時」に設定  
→ Amazia Market 商品一覧 → 商品が表示されない

## 症状
- Console で `publishStart` を過去日時・`publishEnd` を未来日時に設定して保存
- Market の `/api/products/market` を叩いても対象商品が返ってこない
- amazia-core の `isPublished()` ロジック自体は正常

## 根本原因

**二重の問題**が存在する。

### 問題A: ProductForm.vue が price/stock を送信している（SKU分離後の残留フィールド）

フェーズ10設計では「商品マスタは価格・在庫を持たない（SKU単位に移行）」とされているが、  
`ProductForm.vue` は今もなお `price` / `stock` フィールドを送信しており、  
かつバリデーションルールで **必須** になっている。

```js
// ProductForm.vue（修正前）
const rules = {
  name:  [{ required: true, message: '商品名は必須です' }],
  price: [{ required: true, message: '価格は必須です' }],   // ← SKU移行後は不要
  stock: [{ required: true, message: '在庫数は必須です' }], // ← SKU移行後は不要
};
```

price・stock が必須であるため、**公開期間のみ変更したい場合でも price/stock を入力しないとフォームが送信できない**。  
フォーム送信が失敗 → `publishStart` / `publishEnd` が Core に届かない → Market に反映されない。

### 問題B: amazia-core の Product エンティティが price/stock を @NotNull で保持している

`Product.java` が `price` / `stock` に `@NotNull` を付与している。  
フェーズ10でSKUに分離した後もエンティティが更新されておらず、  
Core 側で NULL を受けると 400 Bad Request になる。

```java
// Product.java（修正前）
@NotNull
private Integer price;  // ← SKU移行後は products テーブルには不要

@NotNull
private Integer stock;  // ← SKU移行後は product_sku_stocks が担う
```

### 問題C: Market の `getMarketProducts()` が旧 `/products` エンドポイントも参照できる状態

Market の `products.js` には旧 API（`getProducts`）と新 API（`getMarketProducts`）が混在しており、  
古い実装に戻してしまうリスクがある。ただし現時点では `ProductList.jsx` は `getMarketProducts` を使用中。

## 修正内容

### 1. ProductForm.vue から price・stock フィールドとバリデーションを削除
```vue
<!-- 削除 -->
<a-form-item label="価格（円）" name="price"> ... </a-form-item>
<a-form-item label="在庫数" name="stock"> ... </a-form-item>
```
```js
// rules から price・stock を除去
const rules = {
  name: [{ required: true, message: '商品名は必須です' }],
};
```

### 2. Product.java から price・stock の @NotNull を除去し、フィールド自体を削除
```java
// 修正前
@NotNull
private Integer price;
@NotNull
private Integer stock;

// 修正後: フィールド削除（SKU側で管理）
```

### 3. amazia-console の UpdateProductController・CreateProductController から price/stock 項目を除去

## 再発防止

| 観点 | 対策 |
|------|------|
| 設計変更とコードの乖離 | フェーズ移行時にエンティティ・フォームの廃止フィールドを同時に削除する |
| 公開期間の動作確認 | 「公開期間を設定 → Market に表示されるか」を手動テストチェックリストに追加 |
| 旧 API の誤使用防止 | `getProducts`（旧）を console 専用・`getMarketProducts`（新）を market 専用と明記 |
