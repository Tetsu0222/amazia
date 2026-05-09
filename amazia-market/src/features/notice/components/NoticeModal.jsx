import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Chip,
  IconButton,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { useEffect, useMemo } from 'react';
import { noticeApi } from '../api/notice';

/**
 * フェーズ19: お知らせ本文モーダル。
 *
 * - 開いた瞬間（および前後遷移時）に未読なら markAsRead を発火する。
 * - 「次の／前のお知らせ」は親が一覧の `currentList` から currentIndex±1 の id を解決して
 *   再度 open（API 再呼び出しなし／設計書 §技術検討事項）。
 * - 本文は React の自動エスケープに任せ、改行のみ <br> に変換する（dangerouslySetInnerHTML 禁止）。
 */
export default function NoticeModal({
  open,
  notice,
  hasPrev,
  hasNext,
  onPrev,
  onNext,
  onClose,
  onMarkedAsRead,
}) {
  // モーダルが開いた / notice が変わった瞬間、未読なら markAsRead を発火
  useEffect(() => {
    if (!open || !notice) return;
    if (notice.isRead === false) {
      noticeApi.markAsRead(notice.id)
        .then(() => onMarkedAsRead?.(notice.id))
        .catch(() => {
          // 401 / 404 は黙殺（401 は呼出側のフォールバック / 404 は公開期間外への登録）
        });
    }
  }, [open, notice, onMarkedAsRead]);

  const bodyLines = useMemo(() => String(notice?.body ?? '').split('\n'), [notice?.body]);

  if (!notice) return null;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      role="dialog"
      aria-labelledby="notice-modal-title"
    >
      <DialogTitle
        id="notice-modal-title"
        sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {notice.category?.code && (
            <Chip
              label={notice.category.label ?? notice.category.code}
              size="small"
              color={notice.category.code === 'important' ? 'error' : 'primary'}
            />
          )}
          <Typography component="span" variant="h6">{notice.subject}</Typography>
        </Box>
        <IconButton onClick={onClose} aria-label="閉じる" size="small">
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers>
        <Typography variant="caption" color="text.secondary" component="div" sx={{ mb: 2 }}>
          公開期間：{formatDate(notice.publishStart)} 〜 {formatDate(notice.publishEnd)}
        </Typography>
        <Typography variant="body1" component="div" sx={{ whiteSpace: 'pre-wrap' }}>
          {bodyLines.map((line, i) => (
            <span key={i}>
              {line}
              {i < bodyLines.length - 1 && <br />}
            </span>
          ))}
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={onPrev} disabled={!hasPrev}>前のお知らせ</Button>
        <Button onClick={onNext} disabled={!hasNext}>次のお知らせ</Button>
        <Box sx={{ flexGrow: 1 }} />
        <Button onClick={onClose} variant="contained">閉じる</Button>
      </DialogActions>
    </Dialog>
  );
}

function formatDate(value) {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return `${d.getFullYear()}/${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')}`;
}
