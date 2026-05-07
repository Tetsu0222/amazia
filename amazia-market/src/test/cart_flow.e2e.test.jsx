import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route, Outlet } from 'react-router-dom';

// 顧客 API は jsdom で実 HTTP を叩けないため全面モック
vi.mock('../features/customer/api/customer', () => ({
  registerCustomer: vi.fn(),
  loginCustomer: vi.fn(),
  logoutCustomer: vi.fn(),
  getMyPage: vi.fn(),
  checkEmailAvailability: vi.fn(),
  searchPostalAddresses: vi.fn(),
  requestPasswordReset: vi.fn(),
  confirmPasswordReset: vi.fn(),
  fetchCsrfToken: vi.fn(),
  setCsrfToken: vi.fn(),
  getCsrfToken: vi.fn().mockReturnValue('csrf-test'),
}));
vi.mock('../features/products/api/products', () => ({
  getMarketProducts: vi.fn(),
  getMarketProduct: vi.fn(),
}));
vi.mock('../features/cart/api/cart', () => ({
  getMyCart: vi.fn(),
  addToCart: vi.fn(),
  updateQuantity: vi.fn(),
  removeFromCart: vi.fn(),
  clearCart: vi.fn(),
}));
vi.mock('../features/checkout/api/checkout', () => ({
  confirmOrder: vi.fn(),
  getMyPurchaseHistory: vi.fn(),
}));

import * as customerApi from '../features/customer/api/customer';
import * as productsApi from '../features/products/api/products';
import * as cartApi from '../features/cart/api/cart';
import * as checkoutApi from '../features/checkout/api/checkout';

import ProductList from '../features/products/pages/ProductList';
import ProductDetail from '../features/products/pages/ProductDetail';
import CartPage from '../features/cart/pages/CartPage';
import Checkout from '../features/checkout/pages/Checkout';
import CheckoutComplete from '../features/checkout/pages/CheckoutComplete';
import { AuthProvider } from '../features/customer/context/AuthContext';
import { CartProvider } from '../features/cart/context/CartContext';
import ProtectedRoute from '../features/customer/components/ProtectedRoute';
import AppHeader from '../components/AppHeader';

function Layout() {
  return (
    <>
      <AppHeader />
      <Outlet />
    </>
  );
}

function renderApp(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <CartProvider>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/" element={<ProductList />} />
              <Route path="/products/:id" element={<ProductDetail />} />
              <Route
                path="/cart"
                element={<ProtectedRoute><CartPage /></ProtectedRoute>}
              />
              <Route
                path="/checkout"
                element={<ProtectedRoute><Checkout /></ProtectedRoute>}
              />
              <Route
                path="/checkout/complete"
                element={<ProtectedRoute><CheckoutComplete /></ProtectedRoute>}
              />
            </Route>
          </Routes>
        </CartProvider>
      </AuthProvider>
    </MemoryRouter>
  );
}

const ME = {
  id: 1,
  nameLast: '山田',
  nameFirst: '太郎',
  email: 't@example.com',
  postalCode: '1000001',
  address: '東京都千代田区',
  birthday: '1990-01-01',
  paymentMethod: 'credit_card',
};

const PRODUCT_LIST = [
  { productId: 1, productName: 'テスト商品A', minPrice: 1500, totalStock: 5, preorderStatus: 'ON_SALE', skuCount: 1 },
  { productId: 2, productName: 'りんご', minPrice: 200, totalStock: 3, preorderStatus: 'ON_SALE', skuCount: 1 },
];

const PRODUCT_DETAIL = {
  product: { name: 'テスト商品A', description: '説明' },
  skus: [
    { skuId: 101, color: '赤', size: 'M', price: 1500, stock: 5, images: [] },
  ],
  preorderStatus: 'ON_SALE',
};

const EMPTY_CART = { cartId: null, items: [], totalCount: 0, totalPrice: 0 };

describe('Market カートフロー E2E', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    customerApi.getMyPage.mockResolvedValue(ME);
    customerApi.fetchCsrfToken.mockResolvedValue({ csrfToken: 'csrf-test' });
    productsApi.getMarketProducts.mockResolvedValue(PRODUCT_LIST);
    productsApi.getMarketProduct.mockResolvedValue(PRODUCT_DETAIL);
    cartApi.getMyCart.mockResolvedValue(EMPTY_CART);
  });

  it('商品詳細でカート追加 → カートページ → Checkout(?from=cart) → 注文完了', async () => {
    const user = userEvent.setup();

    // addToCart 後のカート状態
    const filledCart = {
      cartId: 1, totalCount: 1, totalPrice: 1500,
      items: [{
        itemId: 10, skuId: 101, productId: 1, productName: 'テスト商品A',
        color: '赤', size: 'M', unitPrice: 1500, quantity: 1, subtotal: 1500,
        availableStock: 4, preorder: false,
      }],
    };
    cartApi.addToCart.mockResolvedValue(filledCart);
    cartApi.getMyCart.mockResolvedValueOnce(EMPTY_CART).mockResolvedValue(filledCart);
    cartApi.clearCart.mockResolvedValue();
    checkoutApi.confirmOrder.mockResolvedValue({ salesId: 100, paymentId: 'p-1', amount: 1500, quantity: 1 });

    renderApp('/products/1');

    // 商品詳細ロード待ち
    await waitFor(() => expect(productsApi.getMarketProduct).toHaveBeenCalled());
    // 色サイズ選択
    await user.click(await screen.findByRole('button', { name: '赤' }));
    await user.click(await screen.findByRole('button', { name: 'M' }));

    // カート追加
    await user.click(screen.getByRole('button', { name: 'カートに入れる' }));
    await waitFor(() => expect(cartApi.addToCart).toHaveBeenCalledWith(101, 1, false));

    // カートアイコンへ遷移
    await user.click(screen.getByRole('link', { name: 'カート' }));
    await waitFor(() => screen.getByText('カート（1点）'));

    // 「レジに進む」 → Checkout
    await user.click(screen.getByRole('button', { name: 'レジに進む（1 点）' }));
    await waitFor(() => screen.getByText('ご注文内容の確認（カート）'));

    // 注文確定
    await user.click(screen.getByRole('button', { name: '注文を確定する' }));
    await waitFor(() => expect(checkoutApi.confirmOrder).toHaveBeenCalledWith(
      expect.objectContaining({ skuId: 101, quantity: 1, preorder: false })
    ));
    await waitFor(() => expect(cartApi.clearCart).toHaveBeenCalled());

    // 完了画面に到達（カートモードでもトップに戻されない）
    await waitFor(() => expect(screen.getByText('ご注文ありがとうございました。')).toBeInTheDocument());
    expect(screen.getByText(/1 件の注文を承りました/)).toBeInTheDocument();
  });

  it('検索 → 結果0件 → 「すべての商品を見る」で一覧復帰', async () => {
    const user = userEvent.setup();
    renderApp('/');

    await waitFor(() => expect(productsApi.getMarketProducts).toHaveBeenCalled());
    await waitFor(() => screen.getByText('テスト商品A'));

    // ヘッダーの検索バーで存在しないキーワードを送信
    const header = screen.getByRole('banner');
    const searchInputs = within(header).getAllByLabelText('商品検索');
    await user.type(searchInputs[0], 'バナナ{Enter}');

    await waitFor(() => screen.getByText('該当する商品がありません'));
    expect(screen.getByText(/「バナナ」の検索結果（0件）/)).toBeInTheDocument();

    // 「すべての商品を見る」リンク
    await user.click(screen.getByRole('link', { name: 'すべての商品を見る' }));
    await waitFor(() => {
      // q が消えるとヘッダーの「検索結果」見出しは消える
      expect(screen.queryByText(/検索結果/)).not.toBeInTheDocument();
      expect(screen.getByText('テスト商品A')).toBeInTheDocument();
    });
  });

  it('単品 Checkout（?product_id=&sku_id=&quantity=）が引き続き動く（リグレッション）', async () => {
    const user = userEvent.setup();
    checkoutApi.confirmOrder.mockResolvedValue({ salesId: 200, paymentId: 'p-2', amount: 1500, quantity: 1 });

    renderApp('/checkout?product_id=1&sku_id=101&quantity=1');
    await waitFor(() => screen.getByText('ご注文内容の確認'));
    await user.click(screen.getByRole('button', { name: '注文を確定する' }));

    await waitFor(() => expect(checkoutApi.confirmOrder).toHaveBeenCalledWith(
      expect.objectContaining({ skuId: 101, quantity: 1, preorder: false })
    ));
    // カート系 API は呼ばれない
    expect(cartApi.clearCart).not.toHaveBeenCalled();
  });
});
