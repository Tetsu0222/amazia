import { describe, it, expect } from 'vitest';
import { searchProducts } from './searchUtils';

const items = [
  { productId: 1, productName: 'Apple iPhone', description: 'スマートフォン' },
  { productId: 2, productName: 'Google Pixel', description: 'Androidスマホ' },
  { productId: 3, productName: 'チョコレート', description: 'apple フレーバー' },
  { productId: 4, productName: 'みかん', description: null },
];

describe('searchProducts', () => {
  it('キーワードが商品名に含まれる商品を返す', () => {
    expect(searchProducts(items, 'Pixel').map((p) => p.productId)).toEqual([2]);
  });

  it('キーワードが説明文に含まれる商品も返す', () => {
    expect(searchProducts(items, 'スマートフォン').map((p) => p.productId)).toEqual([1]);
  });

  it('大文字小文字を区別しない', () => {
    expect(searchProducts(items, 'APPLE').map((p) => p.productId)).toEqual([1, 3]);
  });

  it('空文字キーワードは全件返す', () => {
    expect(searchProducts(items, '').length).toBe(4);
  });

  it('null や undefined は全件扱い', () => {
    expect(searchProducts(items, null).length).toBe(4);
    expect(searchProducts(items, undefined).length).toBe(4);
  });

  it('description が null の商品もエラーにならない', () => {
    expect(() => searchProducts(items, 'みかん')).not.toThrow();
    expect(searchProducts(items, 'みかん').map((p) => p.productId)).toEqual([4]);
  });
});
