import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import MyPageInquiryNew from './MyPageInquiryNew';
import * as api from '../api/inquiry';

vi.mock('../api/inquiry');

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
  });

  it('件名・本文・対象種別を入力して送信できる', async () => {
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

  it('対象種別を選ぶと対象ID入力欄が出現する', async () => {
    const user = userEvent.setup();
    renderPage();

    // MUI Select を開く
    await user.click(screen.getByLabelText(/対象種別/));
    await user.click(screen.getByRole('option', { name: '配送について' }));

    expect(screen.getByLabelText(/対象ID/)).toBeInTheDocument();
  });

  it('文字数上限超過の本文では送信ボタンが無効化される', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/件名/), '件名');
    // 4001 文字（上限 4000 超）
    const overText = 'a'.repeat(4001);
    fireEvent.change(screen.getByLabelText(/本文/), { target: { value: overText } });

    expect(screen.getByRole('button', { name: '送信' })).toBeDisabled();
  });
});
