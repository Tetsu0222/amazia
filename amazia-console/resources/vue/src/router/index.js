import { createRouter, createWebHistory } from 'vue-router';
import ProductList from '../pages/ProductList.vue';
import ProductForm from '../pages/ProductForm.vue';

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/',                     component: ProductList },
    { path: '/products/new',         component: ProductForm },
    { path: '/products/:id/edit',    component: ProductForm },
  ],
});
