import { useCallback, useEffect, useRef, useState } from 'react';
import { noticeApi } from '../api/notice';

/**
 * ヘッダー用未読お知らせ取得 + 自動ローテーション Hook（R19-7）。
 *
 * - Polling で `nextList` を貯めておき、ローテーション境界（rotateMs ごと）で
 *   `currentList` に差し替える。Polling と setInterval のレースを避ける。
 * - 例外：未読 0 件になった瞬間は即時に `currentList = []`（非表示化）。
 * - インターバル設定：
 *   - 取得間隔（Polling）：`VITE_NOTICE_UNREAD_POLL_MS`（既定 60000ms）
 *   - 自動切替（ローテーション）：`VITE_NOTICE_HEADER_ROTATE_MS`（既定 5000ms）
 */
export function useHeaderNotices() {
  const [currentList, setCurrentList] = useState([]);
  const [activeIndex, setActiveIndex] = useState(0);
  const [authError, setAuthError] = useState(false);

  // 取得直後に貯めておくバッファ（ローテーション境界で current に反映）
  const nextListRef = useRef(null);
  const pollTimerRef = useRef(null);
  const rotateTimerRef = useRef(null);

  const pollMs = Number(import.meta.env.VITE_NOTICE_UNREAD_POLL_MS ?? 60000);
  const rotateMs = Number(import.meta.env.VITE_NOTICE_HEADER_ROTATE_MS ?? 5000);

  const fetchOnce = useCallback(async () => {
    try {
      const list = await noticeApi.fetchUnreadHeader();
      setAuthError(false);
      const arr = Array.isArray(list) ? list : [];
      // 0 件への遷移は即時に反映（境界を待たない）
      if (arr.length === 0) {
        nextListRef.current = null;
        setCurrentList([]);
        setActiveIndex(0);
        return;
      }
      // 初回ロード時など currentList が空なら即時反映（「次の境界」を待たないことで初期表示を早める）
      // 即時反映できたときは ref を空にし、次の境界では「同じリスト内のインデックス送り」を行う。
      setCurrentList((prev) => {
        if (prev.length === 0) {
          nextListRef.current = null;
          return arr;
        }
        nextListRef.current = arr;
        return prev;
      });
    } catch (e) {
      const status = e?.response?.status;
      if (status === 401) {
        setAuthError(true);
        setCurrentList([]);
        nextListRef.current = null;
        setActiveIndex(0);
      }
      // ネットワーク失敗時は UI を変えず黙殺
    }
  }, []);

  // Polling（取得）
  useEffect(() => {
    fetchOnce();
    pollTimerRef.current = setInterval(fetchOnce, pollMs);
    return () => {
      if (pollTimerRef.current) clearInterval(pollTimerRef.current);
    };
  }, [fetchOnce, pollMs]);

  // ローテーション境界：境界で nextList を取り込むか、現リスト内でインデックスを進める
  useEffect(() => {
    rotateTimerRef.current = setInterval(() => {
      const next = nextListRef.current;
      if (next != null) {
        // 新リストへ切替：先頭から表示し、ref は消費済み
        nextListRef.current = null;
        setCurrentList(next);
        setActiveIndex(0);
        return;
      }
      // 同一リスト内でインデックスをサイクル
      setActiveIndex((idx) => {
        const len = currentList.length;
        if (len <= 1) return 0;
        return (idx + 1) % len;
      });
    }, rotateMs);
    return () => {
      if (rotateTimerRef.current) clearInterval(rotateTimerRef.current);
    };
  }, [rotateMs, currentList.length]);

  return { currentList, activeIndex, authError };
}
