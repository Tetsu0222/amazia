import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

// 顧客 API は jsdom で実 HTTP を叩けないため全面モック。
// 「画面遷移 + AuthProvider + ヘッダ + ProtectedRoute」を結合した状態で
// フロントエンドの認証フロー全体（登録 → ログイン → マイページ → ログアウト → パスワード再発行）
// を E2E 風に通すシナリオテスト。
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
}));

// 商品 API（ヘッダ・トップ画面の描画に巻き込まれるため）も無害化
vi.mock('../features/products/api/products', () => ({
  getMarketProducts: vi.fn().mockResolvedValue([]),
  getMarketProduct: vi.fn(),
}));

import * as customerApi from '../features/customer/api/customer';
import * as productsApi from '../features/products/api/products';
import ProductList from '../features/products/pages/ProductList';
import ProductDetail from '../features/products/pages/ProductDetail';
import Login from '../features/customer/pages/Login';
import Register from '../features/customer/pages/Register';
import PasswordResetRequest from '../features/customer/pages/PasswordResetRequest';
import PasswordResetConfirm from '../features/customer/pages/PasswordResetConfirm';
import MyPage from '../features/customer/pages/MyPage';
import { AuthProvider } from '../features/customer/context/AuthContext';
import ProtectedRoute from '../features/customer/components/ProtectedRoute';
import AppHeader from '../components/AppHeader';
import { Outlet } from 'react-router-dom';

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
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<ProductList />} />
            <Route path="/products/:id" element={<ProductDetail />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/password/reset" element={<PasswordResetRequest />} />
            <Route path="/password/reset/confirm" element={<PasswordResetConfirm />} />
            <Route
              path="/mypage"
              element={
                <ProtectedRoute>
                  <MyPage />
                </ProtectedRoute>
              }
            />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  );
}

const SAMPLE_ME = {
  id: 1,
  nameLast: '山田',
  nameFirst: '太郎',
  email: 'taro@example.com',
  postalCode: '1000001',
  address: '東京都千代田区千代田',
  birthday: '1990-01-01',
  paymentMethod: 'credit_card',
};

describe('Market 認証フロー E2E', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // 初期表示は未ログイン（/me が 401）扱い
    customerApi.getMyPage.mockRejectedValue({ response: { status: 401 } });
    customerApi.fetchCsrfToken.mockResolvedValue({ csrfToken: 'csrf-initial' });
    // 商品一覧 API は空配列でリゾルブ（clearAllMocks で実装が消えるため毎回再設定）
    productsApi.getMarketProducts.mockResolvedValue([]);
  });

  it('ヘッダの「ログイン」から /login に遷移できる（未ログイン状態）', async () => {
    renderApp('/');
    await waitFor(() => expect(customerApi.getMyPage).toHaveBeenCalled());

    const header = await screen.findByRole('banner');
    await userEvent.click(within(header).getByRole('link', { name: 'ログイン' }));
    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
  });

  it('未ログインで /mypage にアクセスすると /login にリダイレクトされる', async () => {
    renderApp('/mypage');
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
    );
  });

  it('登録 → ログイン → マイページ → ログアウト の一連フロー', async () => {
    customerApi.checkEmailAvailability.mockResolvedValue({ available: true });
    customerApi.searchPostalAddresses.mockResolvedValue([
      { prefecture: '東京都', city: '千代田区', town: '千代田' },
    ]);
    customerApi.registerCustomer.mockResolvedValue({ id: 1 });
    customerApi.loginCustomer.mockResolvedValue({ csrfToken: 'csrf-after-login' });
    customerApi.logoutCustomer.mockResolvedValue({});

    renderApp('/register');

    // --- 会員登録 ---
    await userEvent.type(screen.getByLabelText(/^姓/), '山田');
    await userEvent.type(screen.getByLabelText(/^名/), '太郎');
    await userEvent.type(screen.getByLabelText(/郵便番号/), '1000001');
    // 単一候補なら住所が自動補完される
    await waitFor(() =>
      expect(screen.getByLabelText(/住所（建物名含む）/)).toHaveValue('東京都千代田区千代田')
    );
    await userEvent.type(screen.getByLabelText(/生年月日/), '1990-01-01');
    await userEvent.type(screen.getByLabelText(/メールアドレス/), 'taro@example.com');
    await waitFor(() => expect(screen.getByText('使用できます')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('パスワード *'), 'Abcdef12');
    await userEvent.type(screen.getByLabelText(/パスワード（確認）/), 'Abcdef12');

    await userEvent.click(screen.getByRole('button', { name: '登録する' }));
    await waitFor(() =>
      expect(customerApi.registerCustomer).toHaveBeenCalledWith(
        expect.objectContaining({
          nameLast: '山田',
          nameFirst: '太郎',
          email: 'taro@example.com',
          postalCode: '1000001',
          paymentMethod: 'credit_card',
          cardToken: 'mock_token',
        })
      )
    );
    // 登録成功後 /login に遷移
    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();

    // --- ログイン（成功時に /me を再取得して認証状態に切替） ---
    customerApi.getMyPage.mockResolvedValue(SAMPLE_ME);

    await userEvent.type(screen.getByLabelText(/メールアドレス/), 'taro@example.com');
    await userEvent.type(screen.getByLabelText(/パスワード/), 'Abcdef12');
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }));

    await waitFor(() =>
      expect(customerApi.loginCustomer).toHaveBeenCalledWith({
        email: 'taro@example.com',
        password: 'Abcdef12',
      })
    );
    expect(customerApi.setCsrfToken).toHaveBeenCalledWith('csrf-after-login');

    // --- マイページ表示 ---
    expect(await screen.findByRole('heading', { name: 'マイページ' })).toBeInTheDocument();
    expect(screen.getByText('山田 太郎')).toBeInTheDocument();
    expect(screen.getAllByText('taro@example.com').length).toBeGreaterThan(0);
    expect(screen.getByText('クレジットカード')).toBeInTheDocument();

    // --- ログアウト ---
    await userEvent.click(screen.getByTestId('logout-button'));
    await waitFor(() => expect(customerApi.logoutCustomer).toHaveBeenCalled());
    // ログアウト後は CSRF クリア（customer null 化はヘッダの再描画で確認）
    expect(customerApi.setCsrfToken).toHaveBeenLastCalledWith(null);
    // ヘッダがゲスト表示（「ログイン」リンク + 「会員登録」ボタン）に戻っている
    await waitFor(() => {
      const header = screen.getByRole('banner');
      expect(within(header).getByRole('link', { name: 'ログイン' })).toBeInTheDocument();
      expect(within(header).getByRole('link', { name: '会員登録' })).toBeInTheDocument();
    });
    // 認証必須のマイページ画面は閉じている
    expect(screen.queryByRole('heading', { name: 'マイページ' })).not.toBeInTheDocument();
  });

  it('ログイン失敗（401）でエラーメッセージが出て認証状態にならない', async () => {
    customerApi.loginCustomer.mockRejectedValue({ response: { status: 401 } });
    renderApp('/login');

    await userEvent.type(screen.getByLabelText(/メールアドレス/), 'taro@example.com');
    await userEvent.type(screen.getByLabelText(/パスワード/), 'wrong-pass');
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }));

    expect(
      await screen.findByText('メールアドレスまたはパスワードが正しくありません。')
    ).toBeInTheDocument();
    // /mypage には行っていない
    expect(screen.queryByRole('heading', { name: 'マイページ' })).not.toBeInTheDocument();
  });

  it('パスワード再発行リクエスト → 確認画面で再設定 → ログイン画面に戻る', async () => {
    customerApi.requestPasswordReset.mockResolvedValue({});
    customerApi.confirmPasswordReset.mockResolvedValue({});

    // --- 申請 ---
    renderApp('/password/reset');
    await userEvent.type(screen.getByLabelText(/メールアドレス/), 'taro@example.com');
    await userEvent.click(screen.getByRole('button', { name: '再発行メールを送信' }));
    await waitFor(() =>
      expect(customerApi.requestPasswordReset).toHaveBeenCalledWith('taro@example.com')
    );
    expect(await screen.findByText(/再設定用のリンクを送信しました/)).toBeInTheDocument();

    // --- リンクからの再設定（別レンダリングでメール内 URL を再現） ---
    customerApi.confirmPasswordReset.mockResolvedValue({});
    const { unmount } = renderApp('/password/reset/confirm?token=valid-token');
    const heading = await screen.findByRole('heading', { name: 'パスワード再設定' });
    expect(heading).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('新しいパスワード *'), 'NewPass12');
    await userEvent.type(screen.getByLabelText(/新しいパスワード（確認）/), 'NewPass12');
    await userEvent.click(screen.getByRole('button', { name: 'パスワードを再設定' }));

    await waitFor(() =>
      expect(customerApi.confirmPasswordReset).toHaveBeenCalledWith('valid-token', 'NewPass12')
    );
    // 自動ログインせずログイン画面へ
    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    unmount();
  });

  it('期限切れトークンでパスワード再設定すると 410 エラーメッセージが表示される', async () => {
    customerApi.confirmPasswordReset.mockRejectedValue({ response: { status: 410 } });
    renderApp('/password/reset/confirm?token=expired-token');

    await userEvent.type(screen.getByLabelText('新しいパスワード *'), 'NewPass12');
    await userEvent.type(screen.getByLabelText(/新しいパスワード（確認）/), 'NewPass12');
    await userEvent.click(screen.getByRole('button', { name: 'パスワードを再設定' }));

    expect(
      await screen.findByText(/リンクの有効期限が切れています/)
    ).toBeInTheDocument();
  });

  it('トークン無しで /password/reset/confirm を開くとエラー文言を出す', async () => {
    renderApp('/password/reset/confirm');
    expect(
      await screen.findByText(/トークンが指定されていません/)
    ).toBeInTheDocument();
  });
});
