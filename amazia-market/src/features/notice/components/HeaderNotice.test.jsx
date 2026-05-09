import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import HeaderNotice from './HeaderNotice';
import { noticeApi } from '../api/notice';

vi.mock('../api/notice', () => ({
  noticeApi: {
    fetchUnreadHeader: vi.fn(),
  },
}));

function renderHeader() {
  return render(
    <MemoryRouter>
      <HeaderNotice />
    </MemoryRouter>
  );
}

describe('HeaderNotice', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('未読 0 件のときヘッダー領域が描画されない', async () => {
    noticeApi.fetchUnreadHeader.mockResolvedValue([]);

    const { container } = renderHeader();

    await waitFor(() => expect(noticeApi.fetchUnreadHeader).toHaveBeenCalled());
    await waitFor(() => expect(container.textContent).toBe(''));
  });

  it('未認証 (401) のときヘッダー領域が描画されない', async () => {
    noticeApi.fetchUnreadHeader.mockRejectedValue({ response: { status: 401 } });

    const { container } = renderHeader();

    await waitFor(() => expect(noticeApi.fetchUnreadHeader).toHaveBeenCalled());
    await waitFor(() => expect(container.textContent).toBe(''));
  });

  it('未読がある場合は最初のお知らせ件名が表示される', async () => {
    noticeApi.fetchUnreadHeader.mockResolvedValue([
      { id: 1, subject: '一件目', category: { code: 'important', label: '重要' } },
      { id: 2, subject: '二件目', category: { code: 'normal',    label: '普通' } },
    ]);

    renderHeader();

    // アコーディオン折りたたみ部分にも全件並んでいるため、件名は複数ヒットする。
    // テストでは「ヘッダーのアクティブ表示として件名が現れること」を「N / M」で代理確認する。
    await waitFor(() => expect(screen.getByText('1 / 2')).toBeInTheDocument());
    expect(screen.getAllByText('一件目').length).toBeGreaterThan(0);
  });

  it('未読が 1 件だけのときは展開ボタンと件数表示を出さない', async () => {
    noticeApi.fetchUnreadHeader.mockResolvedValue([
      { id: 1, subject: '一件目', category: { code: 'important', label: '重要' } },
    ]);

    renderHeader();

    await waitFor(() => expect(screen.getAllByText('一件目').length).toBeGreaterThan(0));
    expect(screen.queryByRole('button', { name: 'お知らせをすべて表示' })).not.toBeInTheDocument();
    expect(screen.queryByText('1 / 1')).not.toBeInTheDocument();
  });

  it('未読が 2 件以上あるときは展開ボタンが描画される', async () => {
    noticeApi.fetchUnreadHeader.mockResolvedValue([
      { id: 1, subject: '一件目', category: { code: 'important', label: '重要' } },
      { id: 2, subject: '二件目', category: { code: 'normal',    label: '普通' } },
    ]);

    renderHeader();

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'お知らせをすべて表示' })).toBeInTheDocument()
    );
  });

  it('5 秒経過すると次のお知らせに切り替わる（fake timer）', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    noticeApi.fetchUnreadHeader.mockResolvedValue([
      { id: 1, subject: '一件目', category: { code: 'important', label: '重要' } },
      { id: 2, subject: '二件目', category: { code: 'normal',    label: '普通' } },
    ]);

    renderHeader();

    // 初期は 1 / 2（active=0）
    await waitFor(() => expect(screen.getByText('1 / 2')).toBeInTheDocument());

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });

    // 5 秒経過後は 2 / 2（active=1）
    await waitFor(() => expect(screen.getByText('2 / 2')).toBeInTheDocument());
  });
});
