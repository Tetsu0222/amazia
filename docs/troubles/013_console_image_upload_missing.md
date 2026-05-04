# 013: Console に画像登録の導線が存在しない

## ステータス
✅ 解決済（2026-05-04）

## 発症箇所
Amazia Console → 商品登録・編集フォーム → 画像アップロード UI がない

## 症状
- Console の商品フォーム（ProductForm.vue）に画像アップロードセクションが存在しない
- フェーズ9・10で実装された画像 API（`POST /api/products/{id}/images` など）へのアクセス手段がない
- ユーザーは画像を登録できず、Market の商品カードはすべて noimage 表示になる

## 根本原因

フェーズ9では amazia-core / amazia-console のバックエンド API（TDD グリーン）は実装済みだが、  
**Console フロントエンド（Vue）に画像アップロード UI が追加されていない**。

```
実装済み ✅                  未実装 ❌
amazia-core: 画像 API ←──── Console UI の画像アップロードフォーム
amazia-console: proxy API        ↑ここが欠落
```

`ProductForm.vue` には商品名・説明・ステータス・公開期間のフィールドのみ存在し、  
画像に関するコードが一切ない。

また `router/index.js` に画像管理専用ページへのルートも未登録。

## 修正内容

### 1. ProductForm.vue に画像アップロードセクションを追加（編集モード限定）

商品作成時は ID がないため、**編集モード（isEdit === true）のみ**画像アップロードを表示する。

```vue
<!-- 編集モード時のみ表示 -->
<template v-if="isEdit">
  <a-divider>商品画像</a-divider>
  <a-form-item label="画像をアップロード（PNG / 200KB以下）">
    <a-upload
      accept=".png,image/png"
      :show-upload-list="false"
      :custom-request="handleImageUpload"
    >
      <a-button>
        <upload-outlined /> 画像を選択
      </a-button>
    </a-upload>
  </a-form-item>

  <!-- 登録済み画像一覧 -->
  <a-space wrap>
    <div v-for="img in images" :key="img.id" style="position: relative">
      <img
        :src="`/storage/Product/images/${img.imagePath}`"
        style="width: 80px; height: 80px; object-fit: contain; border: 1px solid #ddd"
      />
      <a-tag color="blue" v-if="img.sortOrder === 1" style="position: absolute; top: 0; left: 0">
        メイン
      </a-tag>
      <a-button
        size="small"
        danger
        style="display: block; margin-top: 4px"
        @click="handleImageDelete(img.id)"
      >
        削除
      </a-button>
    </div>
  </a-space>
</template>
```

### 2. products.js（Console API）に画像操作関数を追加

```js
export const getProductImages  = (id) => client.get(`/products/${id}/images`).then(r => r.data);
export const uploadProductImage = (id, file) => {
  const form = new FormData();
  form.append('image', file);
  return client.post(`/products/${id}/images`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
export const deleteProductImage = (imageId) => client.delete(`/product-images/${imageId}`);
```

### 3. ProductForm.vue のスクリプトに画像取得・アップロード・削除処理を追加

```js
import { getProductImages, uploadProductImage, deleteProductImage } from '../api/products';

const images = ref([]);

const fetchImages = async () => {
  if (!isEdit.value) return;
  images.value = await getProductImages(route.params.id);
};

const handleImageUpload = async ({ file }) => {
  await uploadProductImage(route.params.id, file);
  await fetchImages();
};

const handleImageDelete = async (imageId) => {
  await deleteProductImage(imageId);
  await fetchImages();
};

onMounted(async () => {
  // 既存の初期化処理に追加
  await fetchImages();
});
```

## 再発防止

| 観点 | 対策 |
|------|------|
| バックエンド先行実装時の UI 漏れ | API 実装 PR とセットで「UI 導線が存在するか」をレビューチェックリストに追加 |
| 新機能の動作確認 | Console の各機能ページを実際にブラウザで操作し、スクリーンショットを PR に添付 |
