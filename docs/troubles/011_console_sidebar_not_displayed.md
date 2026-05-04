# 011: Amazia Console にサイドバーが表示されない

## ステータス
✅ 解決済（2026-05-04）

## 発症箇所
`http://<host>:8001/`（Amazia Console）→ サイドバーが表示されず全幅コンテンツのみ

## 症状
- Console にアクセスするとナビゲーション用のサイドバーが表示されない
- 商品マスタ・SKU管理・画像管理などの各ページへのメニューリンクがない
- 機能自体は URL 直打ちでアクセス可能

## 根本原因

フェーズ10の設計（`phase10_inventory_price_management.md` §2.1）ではサイドメニュー追加が定義されていたが、  
**`App.vue` にサイドバーレイアウトが実装されておらず、`<router-view />` の素通しのみ**になっている。

```vue
<!-- App.vue（修正前）-->
<template>
  <a-config-provider :theme="{ token: { colorPrimary: '#1677ff' } }">
    <router-view />  ← サイドバーなし、レイアウトなし
  </a-config-provider>
</template>
```

また `router/index.js` にも SKU管理・価格管理・在庫管理・画像管理のルートが未登録。

### フェーズ10設計で追加予定だったメニュー
- 商品マスタ（既存）
- SKU管理
- SKU価格管理
- SKU在庫管理
- SKU画像管理
- 商品一覧（SKU集約版）

## なぜ CI で検知できなかったか
- Vue コンポーネントの表示確認は PHPUnit・手動確認のみで E2E テストがない
- フロントエンドのビジュアル確認がデプロイフローに含まれていなかった

## 修正内容

### 1. App.vue に Ant Design の `a-layout` サイドバーレイアウトを追加

```vue
<template>
  <a-config-provider :theme="{ token: { colorPrimary: '#1677ff' } }">
    <a-layout style="min-height: 100vh">
      <a-layout-sider width="200" theme="light">
        <div style="padding: 16px; font-weight: bold; font-size: 16px">Amazia Console</div>
        <a-menu mode="inline" :selected-keys="[currentPath]" @click="handleMenuClick">
          <a-menu-item key="/">商品マスタ</a-menu-item>
          <a-menu-item key="/skus">SKU管理</a-menu-item>
          <a-menu-item key="/sku-prices">SKU価格管理</a-menu-item>
          <a-menu-item key="/sku-stocks">SKU在庫管理</a-menu-item>
          <a-menu-item key="/sku-images">SKU画像管理</a-menu-item>
        </a-menu>
      </a-layout-sider>
      <a-layout>
        <a-layout-content style="padding: 24px">
          <router-view />
        </a-layout-content>
      </a-layout>
    </a-layout>
  </a-config-provider>
</template>
```

### 2. router/index.js に SKU関連ページのルートを追加
```js
import SkuList       from '../features/skus/pages/SkuList.vue';
import SkuPriceList  from '../features/skus/pages/SkuPriceList.vue';
import SkuStockList  from '../features/skus/pages/SkuStockList.vue';
import SkuImageList  from '../features/skus/pages/SkuImageList.vue';

{ path: '/skus',       component: SkuList },
{ path: '/sku-prices', component: SkuPriceList },
{ path: '/sku-stocks', component: SkuStockList },
{ path: '/sku-images', component: SkuImageList },
```

## 再発防止

| 観点 | 対策 |
|------|------|
| レイアウト実装漏れ | 新機能追加時のチェックリストに「App.vue／ルーター更新」を追加 |
| 視覚確認 | デプロイ後にスクリーンショット確認ステップを CI に組み込む |
