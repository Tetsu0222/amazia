import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ProductDetail from './ProductDetail';
import * as api from '../api/products';

vi.mock('../api/products');

// useAuth は describe ブロックごとに上書きできるよう変数経由でモック化
let mockAuthValue = { isAuthenticated: false, customer: null, initializing: false };
vi.mock('../../customer/context/useAuth', () => ({
  useAuth: () => mockAuthValue,
}));

const PRODUCT_DATA = {
  product: { name: 'テストシャツ', description: '説明文です' },
  skus: [
    { id: 101, color: '赤', size: 'S', price: 3000, stock: 10, images: [] },
    { id: 102, color: '赤', size: 'M', price: 3200, stock: 0,  images: [] },
    { id: 103, color: '青', size: 'S', price: 2800, stock: 5,  images: [] },
  ],
};

function renderProductDetail(id = '1') {
  return render(
    <MemoryRouter initialEntries={[`/products/${id}`]}>
      <Routes>
        <Route path="/products/:id" element={<ProductDetail />} />
        <Route path="/login" element={<div data-testid="login-page">ログイン画面</div>} />
        <Route path="/checkout" element={<div data-testid="checkout-page">チェックアウト</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('ProductDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthValue = { isAuthenticated: false, customer: null, initializing: false };
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

  // フェーズ14 r4: 購入導線（ユーザー指示の核心「購入ボタン押下⇒未ログイン⇒ログイン画面」）
  it('未ログイン状態で購入ボタンを押すと /login へリダイレクトする', async () => {
    mockAuthValue = { isAuthenticated: false, customer: null, initializing: false };
    api.getMarketProduct.mockResolvedValue(PRODUCT_DATA);
    renderProductDetail();

    await waitFor(() => screen.getByText('赤'));
    await userEvent.click(screen.getByText('赤'));
    await userEvent.click(screen.getByText('S'));

    const buyButton = screen.getByRole('button', { name: '購入する' });
    expect(buyButton).toBeEnabled();
    await userEvent.click(buyButton);

    expect(screen.getByTestId('login-page')).toBeInTheDocument();
    expect(screen.queryByTestId('checkout-page')).not.toBeInTheDocument();
  });

  it('ログイン済みで購入ボタンを押すと /checkout へ遷移する', async () => {
    mockAuthValue = {
      isAuthenticated: true,
      customer: { id: 1, nameLast: '山田', nameFirst: '太郎' },
      initializing: false,
    };
    api.getMarketProduct.mockResolvedValue(PRODUCT_DATA);
    renderProductDetail();

    await waitFor(() => screen.getByText('赤'));
    await userEvent.click(screen.getByText('赤'));
    await userEvent.click(screen.getByText('S'));

    await userEvent.click(screen.getByRole('button', { name: '購入する' }));

    expect(screen.getByTestId('checkout-page')).toBeInTheDocument();
    expect(screen.queryByTestId('login-page')).not.toBeInTheDocument();
  });

  it('在庫切れの SKU を選択すると購入ボタンが無効化される', async () => {
    mockAuthValue = { isAuthenticated: true, customer: { id: 1 }, initializing: false };
    api.getMarketProduct.mockResolvedValue(PRODUCT_DATA);
    renderProductDetail();

    await waitFor(() => screen.getByText('赤'));
    await userEvent.click(screen.getByText('赤'));
    await userEvent.click(screen.getByText('M')); // 在庫 0

    expect(screen.getByRole('button', { name: '購入する' })).toBeDisabled();
  });
});
