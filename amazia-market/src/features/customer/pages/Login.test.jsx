import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import Login from './Login';

const loginFn = vi.fn();

vi.mock('../context/useAuth', () => ({
  useAuth: () => ({ login: loginFn }),
}));

function renderWithRouter() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/mypage" element={<div>マイページ画面</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('Login', () => {
  beforeEach(() => {
    loginFn.mockReset();
  });

  it('入力値で login を呼び、成功時にマイページへ遷移', async () => {
    loginFn.mockResolvedValue({ email: 'a@b.com' });
    renderWithRouter();
    await userEvent.type(screen.getByLabelText(/メールアドレス/), 'a@b.com');
    await userEvent.type(screen.getByLabelText(/パスワード/), 'Abc12345');
    await userEvent.click(screen.getByRole('button', { name: /ログイン/ }));

    await waitFor(() => expect(loginFn).toHaveBeenCalledWith('a@b.com', 'Abc12345'));
    await waitFor(() => expect(screen.getByText('マイページ画面')).toBeInTheDocument());
  });

  it('401 のときに認証情報エラーを表示', async () => {
    loginFn.mockRejectedValue({ response: { status: 401 } });
    renderWithRouter();
    await userEvent.type(screen.getByLabelText(/メールアドレス/), 'a@b.com');
    await userEvent.type(screen.getByLabelText(/パスワード/), 'badpass');
    await userEvent.click(screen.getByRole('button', { name: /ログイン/ }));

    await waitFor(() =>
      expect(screen.getByText('メールアドレスまたはパスワードが正しくありません。')).toBeInTheDocument()
    );
  });

  it('423 のときにロックエラーを表示', async () => {
    loginFn.mockRejectedValue({ response: { status: 423 } });
    renderWithRouter();
    await userEvent.type(screen.getByLabelText(/メールアドレス/), 'a@b.com');
    await userEvent.type(screen.getByLabelText(/パスワード/), 'badpass');
    await userEvent.click(screen.getByRole('button', { name: /ログイン/ }));

    await waitFor(() =>
      expect(screen.getByText(/アカウントがロック/)).toBeInTheDocument()
    );
  });
});
