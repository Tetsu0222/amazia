import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useUnreadCount } from './useUnreadCount';
import { noticeApi } from '../api/notice';

vi.mock('../api/notice', () => ({
  noticeApi: {
    fetchUnreadCount: vi.fn(),
  },
}));

describe('useUnreadCount', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('初回マウント時に取得した未読数を反映する', async () => {
    noticeApi.fetchUnreadCount.mockResolvedValue({ data: { important: 1, normal: 2, total: 3 } });

    const { result } = renderHook(() => useUnreadCount());

    await waitFor(() => expect(result.current.counts.total).toBe(3));
    expect(result.current.counts).toMatchObject({ important: 1, normal: 2, total: 3 });
    expect(result.current.authError).toBe(false);
  });

  it('401 のとき counts は 0 のまま authError=true', async () => {
    noticeApi.fetchUnreadCount.mockRejectedValue({ response: { status: 401 } });

    const { result } = renderHook(() => useUnreadCount());

    await waitFor(() => expect(result.current.authError).toBe(true));
    expect(result.current.counts).toMatchObject({ important: 0, normal: 0, total: 0 });
  });

  it('既定 60 秒の Polling で再取得される', async () => {
    // 初回はリアルタイマーで設定し、Polling を fake timer で進める。
    vi.useFakeTimers({ shouldAdvanceTime: true });
    noticeApi.fetchUnreadCount
      .mockResolvedValueOnce({ data: { important: 0, normal: 0, total: 0 } })
      .mockResolvedValueOnce({ data: { important: 1, normal: 0, total: 1 } });

    const { result } = renderHook(() => useUnreadCount());

    await waitFor(() => expect(result.current.counts.total).toBe(0));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60000);
    });

    await waitFor(() => expect(result.current.counts.total).toBe(1));
  });
});
