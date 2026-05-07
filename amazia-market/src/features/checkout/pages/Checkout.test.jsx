import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Checkout from './Checkout';
import * as productsApi from '../../products/api/products';
import * as checkoutApi from '../api/checkout';

vi.mock('../../products/api/products');
vi.mock('../api/checkout');

let mockAuthValue = {
  isAuthenticated: true,
  customer: {
    id: 1,
    nameLast: '山田',
    nameFirst: '太郎',
    postalCode: '1500001',
    address: '東京都渋谷区神宮前1-1',
  },
  initializing: false,
};
vi.mock('../../customer/context/useAuth', () => ({
  useAuth: () => mockAuthValue,
}));

let mockCartValue = {
  items: [],
  totalCount: 0,
  totalPrice: 0,
  cartId: null,
  loading: false,
  refresh: vi.fn(),
  addToCart: vi.fn(),
  updateQuantity: vi.fn(),
  removeFromCart: vi.fn(),
  clearCart: vi.fn(),
};
vi.mock('../../cart/context/useCart', () => ({
  useCart: () => mockCartValue,
}));

const PRODUCT_DATA = {
  product: { name: 'テスト商品', description: '' },
  skus: [
    { skuId: 101, color: '赤', size: 'M', price: 3000, stock: 10, images: [] },
    { skuId: 102, color: '青', size: 'L', price: 4000, stock: 0,  images: [] },
  ],
  preorderStatus: 'ON_SALE',
};

function renderCheckout(query) {
  return render(
    <MemoryRouter initialEntries={[`/checkout?${query}`]}>
      <Routes>
        <Route path="/checkout" element={<Checkout />} />
        <Route path="/checkout/complete" element={<div data-testid="complete-page">完了</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('Checkout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    productsApi.getMarketProduct.mockResolvedValue(PRODUCT_DATA);
  });

  // ---- 通常購入モード（既存挙動の回帰確認） -------------------------------

  it('通常モード: タイトルと「注文を確定する」ボタンが表示される', async () => {
    renderCheckout('product_id=1&sku_id=101&quantity=1');

    await waitFor(() => screen.getByText('ご注文内容の確認'));
    expect(screen.getByRole('button', { name: '注文を確定する' })).toBeInTheDocument();
    // 数量ラベル
    expect(screen.getByLabelText('数量')).toBeInTheDocument();
  });

  it('通常モード: 在庫数を超える数量で警告が出てボタンが非活性になる', async () => {
    renderCheckout('product_id=1&sku_id=101&quantity=1');

    await waitFor(() => screen.getByLabelText('数量'));
    const qty = screen.getByLabelText('数量');
    await userEvent.clear(qty);
    await userEvent.type(qty, '99');

    expect(screen.getByText(/在庫数\(10\)を超えています/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '注文を確定する' })).toBeDisabled();
  });

  it('通常モード: confirmOrder に preorder:false で送信される', async () => {
    checkoutApi.confirmOrder.mockResolvedValue({
      salesId: 1, paymentId: 'pay-1', amount: 3000, quantity: 1,
    });
    renderCheckout('product_id=1&sku_id=101&quantity=1');

    await waitFor(() => screen.getByRole('button', { name: '注文を確定する' }));
    await userEvent.click(screen.getByRole('button', { name: '注文を確定する' }));

    await waitFor(() => {
      expect(checkoutApi.confirmOrder).toHaveBeenCalledWith(
        expect.objectContaining({ skuId: 101, quantity: 1, preorder: false })
      );
    });
  });

  // ---- 予約モード（フェーズ14.5） -----------------------------------------

  it('予約モード: タイトルが「ご予約内容の確認」、ボタンが「予約を確定する」', async () => {
    renderCheckout('product_id=1&sku_id=102&quantity=1&preorder=1');

    await waitFor(() => screen.getByText('ご予約内容の確認'));
    expect(screen.getByRole('button', { name: '予約を確定する' })).toBeInTheDocument();
    // 数量ラベルが「予約数」
    expect(screen.getByLabelText('予約数')).toBeInTheDocument();
  });

  it('予約モード: 在庫切れSKU(stock=0)でも在庫警告が出ずボタンが活性', async () => {
    renderCheckout('product_id=1&sku_id=102&quantity=3&preorder=1');

    await waitFor(() => screen.getByLabelText('予約数'));
    expect(screen.queryByText(/在庫数.*を超えています/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '予約を確定する' })).toBeEnabled();
  });

  it('予約モード: 数量を在庫より大きく入力しても警告が出ない', async () => {
    renderCheckout('product_id=1&sku_id=101&quantity=1&preorder=1');

    await waitFor(() => screen.getByLabelText('予約数'));
    const qty = screen.getByLabelText('予約数');
    await userEvent.clear(qty);
    await userEvent.type(qty, '99');

    expect(screen.queryByText(/在庫数.*を超えています/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '予約を確定する' })).toBeEnabled();
  });

  it('予約モード: 価格未設定 SKU は合計が「価格未定」と表示される', async () => {
    productsApi.getMarketProduct.mockResolvedValue({
      product: { name: '価格未定商品', description: '' },
      skus: [{ skuId: 301, color: 'DEFAULT', size: 'FREE', price: null, stock: 0, images: [] }],
      preorderStatus: 'PRE_ORDER',
    });
    renderCheckout('product_id=9&sku_id=301&quantity=1&preorder=1');

    await waitFor(() => screen.getByText('ご予約内容の確認'));
    expect(screen.getByText('価格未定')).toBeInTheDocument();
    // 0 円表記が出ていないこと
    expect(screen.queryByText('¥0')).not.toBeInTheDocument();
  });

  it('予約モード: confirmOrder に preorder:true で送信される', async () => {
    checkoutApi.confirmOrder.mockResolvedValue({
      salesId: 2, paymentId: 'pay-2', amount: 4000, quantity: 1,
    });
    renderCheckout('product_id=1&sku_id=102&quantity=1&preorder=1');

    await waitFor(() => screen.getByRole('button', { name: '予約を確定する' }));
    await userEvent.click(screen.getByRole('button', { name: '予約を確定する' }));

    await waitFor(() => {
      expect(checkoutApi.confirmOrder).toHaveBeenCalledWith(
        expect.objectContaining({ skuId: 102, quantity: 1, preorder: true })
      );
    });
  });

  // ---- カートモード（?from=cart）フェーズ16.5 -----------------------------

  describe('カートモード（?from=cart）', () => {
    const cartItems = [
      { itemId: 10, skuId: 101, productId: 1, productName: 'カート商品A', color: '赤', size: 'M', unitPrice: 3000, quantity: 2, subtotal: 6000, availableStock: 5, preorder: false },
      { itemId: 11, skuId: 102, productId: 1, productName: 'カート商品B', color: '青', size: 'L', unitPrice: 4000, quantity: 1, subtotal: 4000, availableStock: 3, preorder: false },
    ];

    beforeEach(() => {
      mockCartValue = {
        items: cartItems,
        totalCount: 3,
        totalPrice: 10000,
        cartId: 1,
        loading: false,
        refresh: vi.fn(),
        addToCart: vi.fn(),
        updateQuantity: vi.fn(),
        removeFromCart: vi.fn(),
        clearCart: vi.fn().mockResolvedValue(),
      };
    });

    it('?from=cart のときカート明細が全件表示される', async () => {
      renderCheckout('from=cart');
      await waitFor(() => screen.getByText('ご注文内容の確認（カート）'));
      expect(screen.getByText(/カート商品A.*× 2/)).toBeInTheDocument();
      expect(screen.getByText(/カート商品B.*× 1/)).toBeInTheDocument();
      expect(screen.getByText(/¥10,000（3点）/)).toBeInTheDocument();
    });

    it('?from=cart のとき確定で各 SKU が confirmOrder され clearCart が呼ばれる', async () => {
      checkoutApi.confirmOrder
        .mockResolvedValueOnce({ salesId: 100, amount: 6000, quantity: 2 })
        .mockResolvedValueOnce({ salesId: 101, amount: 4000, quantity: 1 });

      renderCheckout('from=cart');
      await waitFor(() => screen.getByRole('button', { name: '注文を確定する' }));
      await userEvent.click(screen.getByRole('button', { name: '注文を確定する' }));

      await waitFor(() => expect(checkoutApi.confirmOrder).toHaveBeenCalledTimes(2));
      expect(checkoutApi.confirmOrder).toHaveBeenNthCalledWith(1,
        expect.objectContaining({ skuId: 101, quantity: 2, preorder: false }));
      expect(checkoutApi.confirmOrder).toHaveBeenNthCalledWith(2,
        expect.objectContaining({ skuId: 102, quantity: 1, preorder: false }));
      await waitFor(() => expect(mockCartValue.clearCart).toHaveBeenCalled());
    });
  });
});
