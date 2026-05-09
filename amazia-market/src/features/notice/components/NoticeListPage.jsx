import {
  Box,
  Container,
  Tabs,
  Tab,
  List,
  ListItemButton,
  ListItemText,
  Pagination,
  Typography,
  Chip,
  CircularProgress,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { noticeApi } from '../api/notice';
import NoticeModal from './NoticeModal';

const PER_PAGE = 20;

/**
 * フェーズ19: Market お知らせ一覧画面（/notices）。
 *
 * - タブで「すべて / 重要 / 普通」を切替（重要 / 普通フィルタは category_id クエリで Core に渡す）。
 * - 行クリックでモーダルを開き、開いた瞬間に未読なら markAsRead を発火（Modal 内で実装）。
 * - 並び順は Core 側で決定（category_id ASC → publish_start DESC → id DESC）。
 */
export default function NoticeListPage() {
  const [tab, setTab] = useState('all'); // all / important / normal
  const [categories, setCategories] = useState([]);
  const [page, setPage] = useState(1);
  const [list, setList] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [openIndex, setOpenIndex] = useState(-1); // モーダル表示中のインデックス（-1 で閉）

  const categoryId = useMemo(() => {
    if (tab === 'all') return undefined;
    const cat = categories.find((c) => c.code === tab);
    return cat?.id;
  }, [tab, categories]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await noticeApi.fetchList({
        page,
        per_page: PER_PAGE,
        category_id: categoryId,
      });
      setList(Array.isArray(res?.content) ? res.content : []);
      setTotal(res?.totalElements ?? 0);
    } catch (e) {
      // 失敗時は空表示で fail-soft
      setList([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, categoryId]);

  useEffect(() => {
    noticeApi.fetchCategories()
      .then((data) => setCategories(Array.isArray(data) ? data : []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const onTabChange = (_e, value) => {
    setTab(value);
    setPage(1);
  };

  const totalPages = Math.max(1, Math.ceil(total / PER_PAGE));

  const handleOpen = (idx) => setOpenIndex(idx);
  const handleClose = () => setOpenIndex(-1);
  const handlePrev = () => setOpenIndex((i) => Math.max(0, i - 1));
  const handleNext = () => setOpenIndex((i) => Math.min(list.length - 1, i + 1));

  const onMarkedAsRead = useCallback((id) => {
    setList((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)));
  }, []);

  const currentNotice = openIndex >= 0 ? list[openIndex] : null;

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Typography variant="h5" component="h1" sx={{ mb: 2 }}>お知らせ</Typography>

      <Tabs value={tab} onChange={onTabChange} sx={{ mb: 2 }}>
        <Tab value="all" label="すべて" />
        <Tab value="important" label="重要" />
        <Tab value="normal" label="普通" />
      </Tabs>

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      ) : list.length === 0 ? (
        <Typography color="text.secondary" sx={{ py: 4 }}>該当するお知らせはありません。</Typography>
      ) : (
        <List>
          {list.map((n, idx) => (
            <ListItemButton key={n.id} onClick={() => handleOpen(idx)} divider>
              <Chip
                label={n.category?.label ?? n.category?.code ?? ''}
                size="small"
                color={n.category?.code === 'important' ? 'error' : 'primary'}
                sx={{ mr: 2, minWidth: 56 }}
              />
              <ListItemText
                primary={n.subject}
                secondary={`更新：${formatDate(n.updatedAt)}`}
              />
              {n.isRead === false && (
                <Chip label="未読" size="small" color="warning" sx={{ ml: 2 }} />
              )}
            </ListItemButton>
          ))}
        </List>
      )}

      {totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Pagination
            count={totalPages}
            page={page}
            onChange={(_e, v) => setPage(v)}
          />
        </Box>
      )}

      <NoticeModal
        open={openIndex >= 0}
        notice={currentNotice}
        hasPrev={openIndex > 0}
        hasNext={openIndex >= 0 && openIndex < list.length - 1}
        onPrev={handlePrev}
        onNext={handleNext}
        onClose={handleClose}
        onMarkedAsRead={onMarkedAsRead}
      />
    </Container>
  );
}

function formatDate(value) {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return `${d.getFullYear()}/${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}
