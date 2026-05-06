import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AppHeader from './AppHeader';

const useAuthMock = vi.fn();
vi.mock('../features/customer/context/useAuth', () => ({
  useAuth: () => useAuthMock(),
}));

describe('AppHeader', () => {
  it('未ログイン時はログイン/会員登録ボタンが表示される', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: false, customer: null, logout: vi.fn() });
    render(<MemoryRouter><AppHeader /></MemoryRouter>);
    expect(screen.getByRole('link', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '会員登録' })).toBeInTheDocument();
  });

  it('ログイン時はマイページ/ログアウトボタンとメールが表示される', () => {
    useAuthMock.mockReturnValue({
      isAuthenticated: true,
      customer: { email: 'a@b.com' },
      logout: vi.fn(),
    });
    render(<MemoryRouter><AppHeader /></MemoryRouter>);
    expect(screen.getByRole('link', { name: 'マイページ' })).toBeInTheDocument();
    expect(screen.getByTestId('logout-button')).toBeInTheDocument();
    expect(screen.getByText('a@b.com')).toBeInTheDocument();
  });
});
