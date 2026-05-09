import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import MyPageInquiryNew from './MyPageInquiryNew';
import * as api from '../api/inquiry';
import * as checkoutApi from '../../checkout/api/checkout';

vi.mock('../api/inquiry');
vi.mock('../../checkout/api/checkout');

function renderPage(initialEntry = '/mypage/inquiries/new') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/mypage/inquiries/new" element={<MyPageInquiryNew />} />
        <Route path="/mypage/inquiries/:id" element={<div data-testid="detail">DETAIL</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('MyPageInquiryNew', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    checkoutApi.getMyPurchaseHistory.mockResolvedValue([]);
  });

  it('種別「指定なし」・対象未選択で送信すると subject 接頭辞なし・target null で送られる', async () => {
    api.createInquiry.mockResolvedValue({ id: 99 });
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/件名/), '配送について');
    await user.type(screen.getByLabelText(/本文/), '配送が遅れているのですが、いつ届きますか？');

    await user.click(screen.getByRole('button', { name: '送信' }));

    await waitFor(() => expect(api.createInquiry).toHaveBeenCalledTimes(1));
    expect(api.createInquiry).toHaveBeenCalledWith(expect.objectContaining({
      subject: '配送について',
      message: '配送が遅れているのですが、いつ届きますか？',
      targetType: null,
      targetId: null,
    }));

    await waitFor(() => expect(screen.getByTestId('detail')).toBeInTheDocument());
  });

  it('種別を選ぶと subject に接頭辞が自動付与される', async () => {
    api.createInquiry.mockResolvedValue({ id: 100 });
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/件名/), '届きません');
    await user.type(screen.getByLabelText(/本文/), 'まだ届いていません');

    await user.click(screen.getByLabelText(/お問い合わせ種別/));
    await user.click(await screen.findByRole('option', { name: '配送について' }));

    await user.click(screen.getByRole('button', { name: '送信' }));

    await waitFor(() => expect(api.createInquiry).toHaveBeenCalledTimes(1));
    expect(api.createInquiry).toHaveBeenCalledWith(expect.objectContaining({
      subject: '[配送について] 届きません',
    }));
  });

  it('購入履歴から対象を選ぶと target_type=sales / target_id=salesId で送信される', async () => {
    checkoutApi.getMyPurchaseHistory.mockResolvedValue([
      { salesId: 42, salesDate: '2026-05-01', productName: 'Tシャツ', color: '白', size: 'M' },
    ]);
    api.createInquiry.mockResolvedValue({ id: 101 });
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/件名/), 'サイズ違いが届いた');
    await user.type(screen.getByLabelText(/本文/), 'M を頼んだのに L が届きました');

    await waitFor(() => expect(checkoutApi.getMyPurchaseHistory).toHaveBeenCalled());

    await user.click(screen.getByLabelText(/関連する購入履歴/));
    await user.click(await screen.findByRole('option', { name: /Tシャツ/ }));

    await user.click(screen.getByRole('button', { name: '送信' }));

    await waitFor(() => expect(api.createInquiry).toHaveBeenCalledTimes(1));
    expect(api.createInquiry).toHaveBeenCalledWith(expect.objectContaining({
      targetType: 'sales',
      targetId: 42,
    }));
  });

  it('文字数上限超過の本文では送信ボタンが無効化される', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/件名/), '件名');
    const overText = 'a'.repeat(4001);
    fireEvent.change(screen.getByLabelText(/本文/), { target: { value: overText } });

    expect(screen.getByRole('button', { name: '送信' })).toBeDisabled();
  });
});
