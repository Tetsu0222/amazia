import { createRouter, createWebHistory } from 'vue-router';
import ProductList   from '../features/products/pages/ProductList.vue';
import ProductForm   from '../features/products/pages/ProductForm.vue';
import ProductImport from '../features/products/pages/ProductImport.vue';
import SkuList       from '../features/skus/pages/SkuList.vue';
import SkuPriceList  from '../features/skus/pages/SkuPriceList.vue';
import SkuStockList  from '../features/skus/pages/SkuStockList.vue';
import SkuImageList  from '../features/skus/pages/SkuImageList.vue';

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/',                     component: ProductList },
    { path: '/products/new',         component: ProductForm },
    { path: '/products/:id/edit',    component: ProductForm },
    { path: '/products/import',      component: ProductImport },
    { path: '/skus',                 component: SkuList },
    { path: '/sku-prices',           component: SkuPriceList },
    { path: '/sku-stocks',           component: SkuStockList },
    { path: '/sku-images',           component: SkuImageList },
  ],
});
