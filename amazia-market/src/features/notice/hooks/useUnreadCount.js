import { useCallback, useEffect, useRef, useState } from 'react';
import { noticeApi } from '../api/notice';

/**
 * Market 会員の未読お知らせ数を Polling で取得する Hook。
 *
 * - インターバルは `VITE_NOTICE_UNREAD_POLL_MS`（既定 60000ms）から取得（規約 4-1）。
 * - 401（未ログイン）は黙殺してカウントを 0 のまま保つ（ヘッダ非表示の役割は呼出側）。
 * - `refresh()` を返し、既読登録直後など即時更新したい場面で呼べる。
 */
export function useUnreadCount() {
  const [counts, setCounts] = useState({ important: 0, normal: 0, total: 0 });
  const [authError, setAuthError] = useState(false);
  const timerRef = useRef(null);

  const intervalMs = Number(
    import.meta.env.VITE_NOTICE_UNREAD_POLL_MS ?? 60000
  );

  const fetchOnce = useCallback(async () => {
    try {
      const res = await noticeApi.fetchUnreadCount();
      setAuthError(false);
      setCounts(res?.data ?? { important: 0, normal: 0, total: 0 });
    } catch (e) {
      const status = e?.response?.status;
      if (status === 401) {
        setAuthError(true);
        setCounts({ important: 0, normal: 0, total: 0 });
      }
      // それ以外のエラーは UI を変えず黙殺（ネットワーク瞬断は次回 Polling で復帰）
    }
  }, []);

  useEffect(() => {
    fetchOnce();
    timerRef.current = setInterval(fetchOnce, intervalMs);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [fetchOnce, intervalMs]);

  return { counts, authError, refresh: fetchOnce };
}
