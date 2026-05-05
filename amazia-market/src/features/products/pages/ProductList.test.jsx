import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ProductList from './ProductList';
import * as api from '../api/products';

vi.mock('../api/products');

function renderProductList() {
  return render(
    <MemoryRouter>
      <ProductList />
    </MemoryRouter>
  );
}

describe('ProductList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('ローディング中はスピナーを表示する', () => {
    api.getMarketProducts.mockReturnValue(new Promise(() => {}));
    renderProductList();
    expect(document.querySelector('.MuiCircularProgress-root')).toBeInTheDocument();
  });

  it('商品一覧を取得して表示する', async () => {
    api.getMarketProducts.mockResolvedValue([
      { productId: 1, productName: 'テスト商品A', minPrice: 1000, totalStock: 5 },
      { productId: 2, productName: 'テスト商品B', minPrice: 2000, totalStock: 0 },
    ]);
    renderProductList();

    await waitFor(() => {
      expect(screen.getByText('テスト商品A')).toBeInTheDocument();
      expect(screen.getByText('テスト商品B')).toBeInTheDocument();
    });
  });

  it('API エラー時にエラーメッセージを表示する', async () => {
    api.getMarketProducts.mockRejectedValue(new Error('network error'));
    renderProductList();

    await waitFor(() => {
      expect(screen.getByText('商品データの取得に失敗しました')).toBeInTheDocument();
    });
  });

  it('商品が 0 件のとき「現在表示できる商品がありません」を表示する', async () => {
    api.getMarketProducts.mockResolvedValue([]);
    renderProductList();

    await waitFor(() => {
      expect(screen.getByText('現在表示できる商品がありません。')).toBeInTheDocument();
    });
  });

  it('価格を ¥1,000 〜 の形式で表示する', async () => {
    api.getMarketProducts.mockResolvedValue([
      { productId: 1, productName: '価格テスト', minPrice: 1000, totalStock: 1 },
    ]);
    renderProductList();

    await waitFor(() => {
      expect(screen.getByText('¥1,000 〜')).toBeInTheDocument();
    });
  });
});
