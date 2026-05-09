import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import AppHeader from './AppHeader';

const useAuthMock = vi.fn();
vi.mock('../features/customer/context/useAuth', () => ({
  useAuth: () => useAuthMock(),
}));

const useUnreadCountMock = vi.fn();
vi.mock('../features/notice/hooks/useUnreadCount', () => ({
  useUnreadCount: () => useUnreadCountMock(),
}));

describe('AppHeader', () => {
  beforeEach(() => {
    useUnreadCountMock.mockReturnValue({
      counts: { important: 0, normal: 0, total: 0 },
      authError: false,
      refresh: vi.fn(),
    });
  });

  it('メインバーにロゴ・検索フィールド・カートアイコンが存在する', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: false, customer: null, logout: vi.fn() });
    render(<MemoryRouter><AppHeader /></MemoryRouter>);
    expect(screen.getByRole('link', { name: 'Amazia Market' })).toBeInTheDocument();
    // SearchBar はメインバー用とモバイル用の2インスタンスが描画される
    expect(screen.getAllByLabelText('商品検索').length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'カート' })).toBeInTheDocument();
  });

  it('未読0件でもお知らせアイコンが /notices へのリンクとして常設される', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: false, customer: null, logout: vi.fn() });
    render(<MemoryRouter><AppHeader /></MemoryRouter>);
    expect(screen.getByRole('link', { name: 'お知らせ' })).toHaveAttribute('href', '/notices');
  });

  it('未読がある場合はお知らせアイコンの aria-label に件数が含まれる', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: false, customer: null, logout: vi.fn() });
    useUnreadCountMock.mockReturnValue({
      counts: { important: 1, normal: 2, total: 3 },
      authError: false,
      refresh: vi.fn(),
    });
    render(<MemoryRouter><AppHeader /></MemoryRouter>);
    expect(screen.getByRole('link', { name: 'お知らせ（未読3件）' })).toHaveAttribute('href', '/notices');
  });

  it('未ログイン時はアカウントメニューにログイン/会員登録が表示される', async () => {
    const user = userEvent.setup();
    useAuthMock.mockReturnValue({ isAuthenticated: false, customer: null, logout: vi.fn() });
    render(<MemoryRouter><AppHeader /></MemoryRouter>);
    await user.click(screen.getByRole('button', { name: 'アカウント＆リスト' }));
    expect(screen.getByRole('menuitem', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: '会員登録' })).toBeInTheDocument();
  });

  it('ログイン時はマイページ/購入履歴/ログアウトとメールが表示される', async () => {
    const user = userEvent.setup();
    useAuthMock.mockReturnValue({
      isAuthenticated: true,
      customer: { email: 'a@b.com' },
      logout: vi.fn(),
    });
    render(<MemoryRouter><AppHeader /></MemoryRouter>);
    expect(screen.getByText(/a@b\.com/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'アカウント＆リスト' }));
    expect(screen.getByRole('menuitem', { name: 'マイページ' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: '購入履歴' })).toBeInTheDocument();
    expect(screen.getByTestId('logout-button')).toBeInTheDocument();
  });

  it('カートアイコンは /cart へのリンクである', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: false, customer: null, logout: vi.fn() });
    render(<MemoryRouter><AppHeader /></MemoryRouter>);
    expect(screen.getByRole('link', { name: 'カート' })).toHaveAttribute('href', '/cart');
  });
});
