import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  Alert,
  Button,
  Container,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { createInquiry } from '../api/inquiry';

const SUBJECT_MAX = 100;
const MESSAGE_MAX = 4000;

const TARGET_TYPE_OPTIONS = [
  { value: '',         label: '指定なし（汎用のお問い合わせ）' },
  { value: 'sales',    label: '注文について' },
  { value: 'delivery', label: '配送について' },
  { value: 'product',  label: '商品について' },
];

export default function MyPageInquiryNew() {
  const navigate = useNavigate();
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [targetType, setTargetType] = useState('');
  const [targetId, setTargetId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const subjectError = subject.length > SUBJECT_MAX
    ? `件名は ${SUBJECT_MAX} 文字以内で入力してください` : null;
  const messageError = message.length > MESSAGE_MAX
    ? `本文は ${MESSAGE_MAX} 文字以内で入力してください` : null;

  const canSubmit =
    subject.trim() && message.trim() && !subjectError && !messageError &&
    (!targetType || targetId);

  const onSubmit = async (e) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      const result = await createInquiry({
        subject: subject.trim(),
        message: message.trim(),
        targetType: targetType || null,
        targetId: targetType ? Number(targetId) : null,
      });
      navigate(`/mypage/inquiries/${result.id}`);
    } catch (e) {
      setError(e?.response?.data?.message || '送信に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Container maxWidth="sm" sx={{ py: 4 }}>
      <Typography variant="h5" mb={2}>新規問い合わせ</Typography>

      <Paper sx={{ p: 3 }}>
        <form onSubmit={onSubmit}>
          <Stack spacing={2}>
            <TextField
              label="件名"
              required
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              error={!!subjectError}
              helperText={subjectError || `${subject.length} / ${SUBJECT_MAX}`}
              inputProps={{ maxLength: SUBJECT_MAX + 10 }}
            />
            <TextField
              select
              label="対象種別"
              value={targetType}
              onChange={(e) => { setTargetType(e.target.value); setTargetId(''); }}
            >
              {TARGET_TYPE_OPTIONS.map((o) => (
                <MenuItem key={o.value || 'none'} value={o.value}>{o.label}</MenuItem>
              ))}
            </TextField>
            {targetType && (
              <TextField
                label={`対象ID（${TARGET_TYPE_OPTIONS.find(o => o.value === targetType)?.label}）`}
                required
                type="number"
                value={targetId}
                onChange={(e) => setTargetId(e.target.value)}
                helperText="マイページの注文・配送履歴等から ID を確認してください"
              />
            )}
            <TextField
              label="本文"
              required
              multiline
              minRows={6}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              error={!!messageError}
              helperText={messageError || `${message.length} / ${MESSAGE_MAX}`}
              inputProps={{ maxLength: MESSAGE_MAX + 100 }}
            />
            {error && <Alert severity="error">{error}</Alert>}
            <Stack direction="row" spacing={1} justifyContent="flex-end">
              <Button component={Link} to="/mypage/inquiries" variant="outlined">キャンセル</Button>
              <Button type="submit" variant="contained" disabled={!canSubmit || submitting}>
                送信
              </Button>
            </Stack>
          </Stack>
        </form>
      </Paper>
    </Container>
  );
}
