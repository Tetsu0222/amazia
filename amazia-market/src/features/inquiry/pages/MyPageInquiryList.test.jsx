import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MyPageInquiryList from './MyPageInquiryList';
import * as api from '../api/inquiry';

vi.mock('../api/inquiry');

function renderPage() {
  return render(
    <MemoryRouter>
      <MyPageInquiryList />
    </MemoryRouter>
  );
}

describe('MyPageInquiryList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('問い合わせ一覧を表示する', async () => {
    api.listMyInquiries.mockResolvedValue({
      content: [
        {
          id: 1, subject: '配送が遅いです', status: 'NEW',
          updatedAt: '2026-05-09T10:00:00', userName: '山田 太郎',
        },
        {
          id: 2, subject: '商品について', status: 'DONE',
          updatedAt: '2026-05-08T10:00:00', userName: '山田 太郎',
        },
      ],
      totalElements: 2,
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('配送が遅いです')).toBeInTheDocument());
    expect(screen.getByText('商品について')).toBeInTheDocument();
    expect(screen.getByText('未対応')).toBeInTheDocument();
    expect(screen.getByText('完了')).toBeInTheDocument();
  });

  it('問い合わせが0件のときは空状態を表示する', async () => {
    api.listMyInquiries.mockResolvedValue({ content: [], totalElements: 0 });

    renderPage();

    await waitFor(() =>
      expect(screen.getByText('問い合わせはまだありません')).toBeInTheDocument());
  });

  it('API 失敗時はエラーメッセージを表示する', async () => {
    api.listMyInquiries.mockRejectedValue({
      response: { data: { message: 'session required' } },
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('session required')).toBeInTheDocument());
  });
});
