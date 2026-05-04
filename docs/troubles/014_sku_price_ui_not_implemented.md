# 014: SKU価格管理ページに「フェーズ10で実装予定」と表示される

## ステータス
🟢 解決済み（2026-05-04）

## 発症箇所
Amazia Console → サイドメニュー「SKU価格管理」→ `http://<host>:8001/sku-prices`

## 症状
- サイドメニューの「SKU価格管理」をクリックすると、ページ遷移はするが内容が表示されない
- ページ内に「このページはフェーズ10で実装予定です。」というアラートのみが表示される
- フェーズ10は実装完了済みのはずだが、未実装を示すメッセージが残ったままになっている

## 調査結果

### これは不具合か？未実装か？

**未実装（フロントエンド UI の実装漏れ）**。

ルーティングや画面遷移は正常に機能している。バックエンドの API も完全に実装済みである。  
問題は **Vue コンポーネント（SkuPriceList.vue）のテンプレートが、フェーズ10完了時に更新されなかった**ことにある。

### 実装状況の層別サマリー

| 層 | 状態 | 詳細 |
|---|---|---|
| Vue Router（フロントルート） | ✅ 実装済み | `/sku-prices` → `SkuPriceList` が登録されている |
| サイドメニュー | ✅ 実装済み | `App.vue` に「SKU価格管理」メニュー項目が存在する |
| amazia-console API ルート | ✅ 実装済み | `GET/POST /api/skus/{id}/prices` が `routes/api/Sku.php` に定義済み |
| amazia-console コントローラー | ✅ 実装済み | `GetProductSkuPriceController`, `CreateProductSkuPriceController` |
| amazia-console サービス層 | ✅ 実装済み | バリデーション・amazia-core 呼び出しロジックが完成 |
| amazia-core API | ✅ 実装済み | `GET/POST /api/skus/{id}/prices` エンドポイント実装済み |
| amazia-core サービス層 | ✅ 実装済み | SKU存在確認・価格保存ロジックが完成 |
| DB エンティティ | ✅ 実装済み | `product_sku_prices` テーブル・`ProductSkuPrice` エンティティ定義済み |
| **フロントエンド UI** | ❌ **未実装** | `SkuPriceList.vue` がプレースホルダーのまま |

### 根本原因

`SkuPriceList.vue` に、フェーズ10の作業着手前に配置したプレースホルダーテンプレートが残っている。  
バックエンドの実装が完了した段階で、このコンポーネントを実際の UI に差し替える作業が抜け落ちた。

```vue
<!-- resources/vue/src/features/skus/pages/SkuPriceList.vue（現状） -->
<template>
  <div style="padding: 24px">
    <a-page-header title="SKU価格管理" sub-title="Amazia Console" />
    <a-alert
      message="このページはフェーズ10で実装予定です。"
      type="info"
      show-icon
    />
  </div>
</template>
```

バックエンド（Laravel + Spring Boot）は正常に動作しており、  
`GET /api/skus/{id}/prices` を叩けば価格データは返ってくる状態である。

### バックエンド API の仕様（実装済み）

| エンドポイント | メソッド | 用途 |
|---|---|---|
| `/api/skus/{id}/prices` | GET | 指定 SKU の価格一覧を取得 |
| `/api/skus/{id}/prices` | POST | 指定 SKU の価格を登録・更新 |

価格エンティティのカラム：`id`, `sku_id`, `price`, `start_date`, `end_date`, `created_at`, `updated_at`

## 修正方針

`SkuPriceList.vue` のテンプレートをプレースホルダーから実際の UI に置き換える。  
既存の API・コントローラー・サービス層は変更不要。

### 修正対象ファイル

- `Amazia/amazia-console/resources/vue/src/features/skus/pages/SkuPriceList.vue`（1ファイルのみ）

### UI に必要な要素（最低限）

1. SKU を選択するプルダウン（`GET /api/skus` で一覧取得）
2. 選択した SKU の価格一覧テーブル（`GET /api/skus/{id}/prices`）
3. 価格登録フォーム（`POST /api/skus/{id}/prices`）
   - 入力項目：価格、適用開始日、適用終了日

## なぜフェーズ10完了時に検知できなかったか

- フェーズ10の完了基準がバックエンド API のテストグリーンのみで定義されていた
- フロントエンドの画面確認（ブラウザでのスモークテスト）が完了条件に含まれていなかった
- プレースホルダーコンポーネントが残っていても、ルーティングやビルドは正常に通るため CI で検知できなかった

## 再発防止

| 観点 | 対策 |
|------|------|
| フェーズ完了基準 | バックエンド API テストに加え、実際の画面遷移・操作確認をフェーズ完了条件に含める |
| プレースホルダー管理 | 「実装予定」テンプレートを使用する場合、該当コンポーネントファイルに `TODO:` コメントを記載し、フェーズ完了 PR のチェックリストで消し込む |
| UI の動作確認 | 新しいメニュー項目を追加した際は、ブラウザで実際にクリックして画面が正しく機能することをスクリーンショット付きで PR に添付する |
