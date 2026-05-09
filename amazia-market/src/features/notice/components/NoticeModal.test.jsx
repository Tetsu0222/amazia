import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import NoticeModal from './NoticeModal';
import { noticeApi } from '../api/notice';

vi.mock('../api/notice', () => ({
  noticeApi: {
    markAsRead: vi.fn(),
  },
}));

describe('NoticeModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const baseNotice = {
    id: 100,
    subject: 'テスト件名',
    category: { code: 'normal', label: '普通' },
    body: '一行目\n二行目\n三行目',
    publishStart: '2026-05-09T00:00:00',
    publishEnd:   '2026-05-15T23:59:59',
    isRead: false,
  };

  it('開いた瞬間に未読なら markAsRead を発火する', async () => {
    noticeApi.markAsRead.mockResolvedValue({});

    render(
      <NoticeModal
        open
        notice={baseNotice}
        hasPrev={false}
        hasNext={false}
        onPrev={() => {}}
        onNext={() => {}}
        onClose={() => {}}
        onMarkedAsRead={() => {}}
      />
    );

    await waitFor(() => expect(noticeApi.markAsRead).toHaveBeenCalledWith(100));
  });

  it('既読のときは markAsRead を呼ばない', async () => {
    noticeApi.markAsRead.mockResolvedValue({});

    render(
      <NoticeModal
        open
        notice={{ ...baseNotice, isRead: true }}
        hasPrev={false}
        hasNext={false}
        onPrev={() => {}}
        onNext={() => {}}
        onClose={() => {}}
        onMarkedAsRead={() => {}}
      />
    );

    // useEffect を待ってから検証
    await new Promise((r) => setTimeout(r, 10));
    expect(noticeApi.markAsRead).not.toHaveBeenCalled();
  });

  it('XSS：本文に HTML タグが含まれていてもエスケープされて表示される', () => {
    const xssNotice = {
      ...baseNotice,
      body: '<script>alert("xss")</script>',
    };

    const { container } = render(
      <NoticeModal
        open
        notice={xssNotice}
        hasPrev={false}
        hasNext={false}
        onPrev={() => {}}
        onNext={() => {}}
        onClose={() => {}}
        onMarkedAsRead={() => {}}
      />
    );

    // <script> タグが DOM に解釈されず、テキストとして表示されること
    expect(container.querySelector('script')).toBeNull();
    expect(screen.getByText(/<script>alert\("xss"\)<\/script>/)).toBeInTheDocument();
  });

  it('「次のお知らせ」「前のお知らせ」が hasPrev / hasNext で活性／非活性になる', () => {
    const { rerender } = render(
      <NoticeModal
        open
        notice={baseNotice}
        hasPrev={false}
        hasNext={true}
        onPrev={() => {}}
        onNext={() => {}}
        onClose={() => {}}
        onMarkedAsRead={() => {}}
      />
    );

    expect(screen.getByRole('button', { name: '前のお知らせ' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '次のお知らせ' })).not.toBeDisabled();

    rerender(
      <NoticeModal
        open
        notice={baseNotice}
        hasPrev={true}
        hasNext={false}
        onPrev={() => {}}
        onNext={() => {}}
        onClose={() => {}}
        onMarkedAsRead={() => {}}
      />
    );

    expect(screen.getByRole('button', { name: '前のお知らせ' })).not.toBeDisabled();
    expect(screen.getByRole('button', { name: '次のお知らせ' })).toBeDisabled();
  });

  it('「次のお知らせ」クリックで onNext が呼ばれる', () => {
    const onNext = vi.fn();

    render(
      <NoticeModal
        open
        notice={baseNotice}
        hasPrev={true}
        hasNext={true}
        onPrev={() => {}}
        onNext={onNext}
        onClose={() => {}}
        onMarkedAsRead={() => {}}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: '次のお知らせ' }));
    expect(onNext).toHaveBeenCalled();
  });
});
