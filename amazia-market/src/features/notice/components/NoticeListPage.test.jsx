import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import NoticeListPage from './NoticeListPage';
import { noticeApi } from '../api/notice';

vi.mock('../api/notice', () => ({
  noticeApi: {
    fetchUnreadCount: vi.fn(),
    fetchUnreadHeader: vi.fn(),
    fetchList: vi.fn(),
    fetchDetail: vi.fn(),
    markAsRead: vi.fn(),
    fetchCategories: vi.fn(),
  },
}));

function renderPage() {
  return render(
    <MemoryRouter>
      <NoticeListPage />
    </MemoryRouter>
  );
}

describe('NoticeListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    noticeApi.fetchCategories.mockResolvedValue([
      { id: 1, code: 'important', label: '重要', displayOrder: 1 },
      { id: 2, code: 'normal',    label: '普通',  displayOrder: 2 },
    ]);
  });

  it('一覧を取得して件名を表示する', async () => {
    noticeApi.fetchList.mockResolvedValue({
      content: [
        { id: 11, subject: '重要なお知らせ', category: { code: 'important', label: '重要' },
          publishStart: '2026-05-09T00:00:00', publishEnd: '2026-05-15T23:59:59',
          updatedAt: '2026-05-09T10:00:00', isRead: false },
        { id: 12, subject: '通常のお知らせ', category: { code: 'normal', label: '普通' },
          publishStart: '2026-05-09T00:00:00', publishEnd: '2026-05-15T23:59:59',
          updatedAt: '2026-05-09T10:00:00', isRead: true },
      ],
      totalElements: 2,
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('重要なお知らせ')).toBeInTheDocument());
    expect(screen.getByText('通常のお知らせ')).toBeInTheDocument();
  });

  it('「重要」タブクリックで category_id クエリを Core に渡す', async () => {
    noticeApi.fetchList.mockResolvedValue({ content: [], totalElements: 0 });

    renderPage();

    // 初期描画完了を待つ
    await waitFor(() => expect(noticeApi.fetchList).toHaveBeenCalled());

    // categories 取得後、タブをクリック
    await waitFor(() => expect(noticeApi.fetchCategories).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('tab', { name: '重要' }));

    await waitFor(() => {
      const lastCall = noticeApi.fetchList.mock.calls.at(-1);
      expect(lastCall?.[0]).toMatchObject({ category_id: 1 });
    });
  });

  it('0 件のとき空状態メッセージを表示する', async () => {
    noticeApi.fetchList.mockResolvedValue({ content: [], totalElements: 0 });

    renderPage();

    await waitFor(() =>
      expect(screen.getByText('該当するお知らせはありません。')).toBeInTheDocument()
    );
  });

  it('行クリックでモーダルが開き未読なら markAsRead が発火する', async () => {
    noticeApi.fetchList.mockResolvedValue({
      content: [{
        id: 21, subject: '未読アイテム', category: { code: 'normal', label: '普通' },
        body: '本文', publishStart: '2026-05-09T00:00:00', publishEnd: '2026-05-15T23:59:59',
        updatedAt: '2026-05-09T10:00:00', isRead: false,
      }],
      totalElements: 1,
    });
    noticeApi.markAsRead.mockResolvedValue({});

    renderPage();

    await waitFor(() => expect(screen.getByText('未読アイテム')).toBeInTheDocument());
    fireEvent.click(screen.getByText('未読アイテム'));

    await waitFor(() => expect(noticeApi.markAsRead).toHaveBeenCalledWith(21));
  });
});
