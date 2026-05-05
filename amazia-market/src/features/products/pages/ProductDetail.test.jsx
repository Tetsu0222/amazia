import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ProductDetail from './ProductDetail';
import * as api from '../api/products';

vi.mock('../api/products');

const PRODUCT_DATA = {
  product: { name: 'テストシャツ', description: '説明文です' },
  skus: [
    { color: '赤', size: 'S', price: 3000, stock: 10, images: [] },
    { color: '赤', size: 'M', price: 3200, stock: 0,  images: [] },
    { color: '青', size: 'S', price: 2800, stock: 5,  images: [] },
  ],
};

function renderProductDetail(id = '1') {
  return render(
    <MemoryRouter initialEntries={[`/products/${id}`]}>
      <Routes>
        <Route path="/products/:id" element={<ProductDetail />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('ProductDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('ローディング中はスピナーを表示する', () => {
    api.getMarketProduct.mockReturnValue(new Promise(() => {}));
    renderProductDetail();
    expect(document.querySelector('.MuiCircularProgress-root')).toBeInTheDocument();
  });

  it('商品名と説明を表示する', async () => {
    api.getMarketProduct.mockResolvedValue(PRODUCT_DATA);
    renderProductDetail();

    await waitFor(() => {
      expect(screen.getByText('テストシャツ')).toBeInTheDocument();
      expect(screen.getByText('説明文です')).toBeInTheDocument();
    });
  });

  it('API エラー時にエラーメッセージを表示する', async () => {
    api.getMarketProduct.mockRejectedValue(new Error('network error'));
    renderProductDetail();

    await waitFor(() => {
      expect(screen.getByText('商品データの取得に失敗しました')).toBeInTheDocument();
    });
  });

  it('色を選択するとサイズが表示される', async () => {
    api.getMarketProduct.mockResolvedValue(PRODUCT_DATA);
    renderProductDetail();

    await waitFor(() => screen.getByText('赤'));
    await userEvent.click(screen.getByText('赤'));

    expect(screen.getByText('S')).toBeInTheDocument();
    expect(screen.getByText('M')).toBeInTheDocument();
  });

  it('色とサイズを選択すると価格と在庫が表示される', async () => {
    api.getMarketProduct.mockResolvedValue(PRODUCT_DATA);
    renderProductDetail();

    await waitFor(() => screen.getByText('赤'));
    await userEvent.click(screen.getByText('赤'));
    await userEvent.click(screen.getByText('S'));

    expect(screen.getByText('¥3,000')).toBeInTheDocument();
    expect(screen.getByText('在庫 10 個')).toBeInTheDocument();
  });

  it('在庫 0 のSKU選択時に「在庫なし」を表示する', async () => {
    api.getMarketProduct.mockResolvedValue(PRODUCT_DATA);
    renderProductDetail();

    await waitFor(() => screen.getByText('赤'));
    await userEvent.click(screen.getByText('赤'));
    await userEvent.click(screen.getByText('M'));

    expect(screen.getByText('在庫なし')).toBeInTheDocument();
  });

  it('色を変えるとサイズがリセットされ価格が消える', async () => {
    api.getMarketProduct.mockResolvedValue(PRODUCT_DATA);
    renderProductDetail();

    await waitFor(() => screen.getByText('赤'));
    await userEvent.click(screen.getByText('赤'));
    await userEvent.click(screen.getByText('S'));
    expect(screen.getByText('¥3,000')).toBeInTheDocument();

    await userEvent.click(screen.getByText('青'));
    expect(screen.queryByText('¥3,000')).not.toBeInTheDocument();
    expect(screen.getByText('色とサイズを選択してください')).toBeInTheDocument();
  });
});
