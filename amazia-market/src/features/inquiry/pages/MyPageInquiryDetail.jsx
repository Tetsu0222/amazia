import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { getMyInquiry, replyMyInquiry } from '../api/inquiry';

const STATUS_LABELS = { NEW: '未対応', IN_PROGRESS: '対応中', DONE: '完了' };
const STATUS_COLORS = { NEW: 'error', IN_PROGRESS: 'info', DONE: 'default' };

export default function MyPageInquiryDetail() {
  const { id } = useParams();
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [reply, setReply] = useState('');
  const [replying, setReplying] = useState(false);
  const [replyError, setReplyError] = useState(null);

  const load = async () => {
    setLoading(true);
    try {
      const data = await getMyInquiry(id);
      setDetail(data);
      setError(null);
    } catch (e) {
      setError(e?.response?.data?.message || '問い合わせの取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [id]);

  const onReply = async () => {
    if (!reply.trim()) return;
    setReplying(true);
    setReplyError(null);
    try {
      await replyMyInquiry(id, reply.trim());
      setReply('');
      await load();
    } catch (e) {
      setReplyError(e?.response?.data?.message || '送信に失敗しました');
    } finally {
      setReplying(false);
    }
  };

  if (loading) return <Container sx={{ py: 4 }}><CircularProgress /></Container>;
  if (error) return <Container sx={{ py: 4 }}><Alert severity="error">{error}</Alert></Container>;
  if (!detail) return null;

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Stack spacing={2} mb={2}>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="h5">{detail.subject}</Typography>
          <Chip
            label={STATUS_LABELS[detail.status] || detail.status}
            color={STATUS_COLORS[detail.status] || 'default'}
          />
        </Stack>
        {detail.targetLabel && (
          <Typography color="text.secondary">対象: {detail.targetLabel}</Typography>
        )}
      </Stack>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack spacing={2}>
          {(detail.messages || []).map((m) => (
            <Box
              key={m.id}
              sx={{
                p: 2,
                borderRadius: 1,
                bgcolor: m.senderType === 'admin_user' ? 'primary.50' : 'grey.100',
                alignSelf: m.senderType === 'admin_user' ? 'flex-end' : 'flex-start',
                maxWidth: '85%',
                whiteSpace: 'pre-wrap',
              }}
            >
              <Stack direction="row" spacing={1} alignItems="center" mb={0.5}>
                <Chip
                  size="small"
                  label={m.senderType === 'admin_user' ? 'サポート' : 'あなた'}
                  color={m.senderType === 'admin_user' ? 'primary' : 'default'}
                />
                <Typography variant="caption" color="text.secondary">
                  {new Date(m.createdAt).toLocaleString('ja-JP')}
                </Typography>
              </Stack>
              <Typography>{m.message}</Typography>
            </Box>
          ))}
        </Stack>
      </Paper>

      {detail.status !== 'DONE' && (
        <Paper sx={{ p: 2 }}>
          <Typography variant="subtitle1" gutterBottom>追加で質問・返信する</Typography>
          <TextField
            multiline
            minRows={4}
            fullWidth
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            placeholder="メッセージを入力"
          />
          {replyError && <Alert severity="error" sx={{ mt: 1 }}>{replyError}</Alert>}
          <Stack direction="row" spacing={1} justifyContent="flex-end" mt={2}>
            <Button component={Link} to="/mypage/inquiries" variant="outlined">戻る</Button>
            <Button
              variant="contained"
              disabled={!reply.trim() || replying}
              onClick={onReply}
            >送信</Button>
          </Stack>
        </Paper>
      )}
    </Container>
  );
}
