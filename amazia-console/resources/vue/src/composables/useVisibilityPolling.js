import { ref, onMounted, onUnmounted } from 'vue';

/**
 * 可視性連動ポーリング Composable（フェーズ18 / RV-8 / 規約 2-3 Shared 思想）。
 *
 * - タブ非表示時は setInterval を停止（document.hidden 連動）
 * - タブ復帰時は即時 fetch + setInterval 再開
 * - unmount 時にクリーンアップ
 *
 * @param {Function} fetcher - 呼び出すたびに Promise<T> を返す関数（1 引数なし）
 * @param {Number}  intervalMs - ポーリング間隔（ミリ秒）
 * @returns {{ data: Ref<T|null>, error: Ref<Error|null> }}
 */
export function useVisibilityPolling(fetcher, intervalMs) {
  const data = ref(null);
  const error = ref(null);
  let timer = null;

  const fetchOnce = async () => {
    try {
      const result = await fetcher();
      data.value = result;
      error.value = null;
    } catch (e) {
      error.value = e;
    }
  };

  const start = () => {
    if (timer) return;
    fetchOnce();
    timer = setInterval(fetchOnce, intervalMs);
  };

  const stop = () => {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  };

  const onVisibilityChange = () => {
    if (document.hidden) {
      stop();
    } else {
      fetchOnce();
      start();
    }
  };

  onMounted(() => {
    start();
    document.addEventListener('visibilitychange', onVisibilityChange);
  });

  onUnmounted(() => {
    stop();
    document.removeEventListener('visibilitychange', onVisibilityChange);
  });

  return { data, error };
}
