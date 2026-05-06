import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import Register from './Register';
import * as api from '../api/customer';

vi.mock('../api/customer');

function renderRegister() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <Routes>
        <Route path="/register" element={<Register />} />
        <Route path="/login" element={<div>ログイン画面</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('Register', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.searchPostalAddresses.mockResolvedValue([]);
    api.checkEmailAvailability.mockResolvedValue({ available: true });
  });

  it('郵便番号入力で住所を自動補完する（候補1件）', async () => {
    api.searchPostalAddresses.mockResolvedValue([
      { postalCode: '1000001', prefecture: '東京都', city: '千代田区', town: '千代田' },
    ]);
    renderRegister();
    await userEvent.type(screen.getByLabelText(/郵便番号/), '1000001');

    await waitFor(() => {
      expect(api.searchPostalAddresses).toHaveBeenCalledWith('1000001');
    }, { timeout: 1000 });
    await waitFor(() => {
      expect(screen.getByLabelText(/住所（建物名含む）/)).toHaveValue('東京都千代田区千代田');
    }, { timeout: 1000 });
  });

  it('メール重複時にエラー表示する', async () => {
    api.checkEmailAvailability.mockResolvedValue({ available: false });
    renderRegister();
    await userEvent.type(screen.getByLabelText(/メールアドレス/), 'used@example.com');

    await waitFor(
      () => expect(screen.getByText('このメールアドレスは既に登録されています')).toBeInTheDocument(),
      { timeout: 1500 }
    );
  });

  it('18歳未満で年齢エラーを表示する', async () => {
    renderRegister();
    const today = new Date();
    const recent = new Date(today.getFullYear() - 10, today.getMonth(), today.getDate())
      .toISOString().slice(0, 10);
    await userEvent.type(screen.getByLabelText(/生年月日/), recent);
    expect(await screen.findByText('18歳未満は登録できません')).toBeInTheDocument();
  });
});
