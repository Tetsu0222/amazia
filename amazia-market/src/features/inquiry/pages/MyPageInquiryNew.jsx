import { useEffect, useState } from 'react';
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
import { getMyPurchaseHistory } from '../../checkout/api/checkout';

const SUBJECT_MAX = 100;
const MESSAGE_MAX = 4000;

const CATEGORY_NONE = '__none__';

const CATEGORY_OPTIONS = [
  { value: CATEGORY_NONE, label: '指定なし' },
  { value: 'delivery',    label: '配送について' },
  { value: 'sales',       label: '注文について' },
  { value: 'product',     label: '商品について' },
  { value: 'other',       label: 'その他' },
];

const TARGET_NONE = '__none__';

export default function MyPageInquiryNew() {
  const navigate = useNavigate();
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [category, setCategory] = useState(CATEGORY_NONE);
  const [target, setTarget] = useState(TARGET_NONE);
  const [purchases, setPurchases] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const data = await getMyPurchaseHistory();
        if (alive) setPurchases(Array.isArray(data) ? data : []);
      } catch {
        if (alive) setPurchases([]);
      }
    })();
    return () => { alive = false; };
  }, []);

  const categoryLabel = CATEGORY_OPTIONS.find((o) => o.value === category)?.label;
  const subjectWithPrefix = (category === CATEGORY_NONE)
    ? subject.trim()
    : `[${categoryLabel}] ${subject.trim()}`;

  const subjectError = subjectWithPrefix.length > SUBJECT_MAX
    ? `件名は ${SUBJECT_MAX} 文字以内で入力してください（種別接頭辞を含めて ${subjectWithPrefix.length} 文字）`
    : null;
  const messageError = message.length > MESSAGE_MAX
    ? `本文は ${MESSAGE_MAX} 文字以内で入力してください` : null;

  const canSubmit =
    subject.trim() && message.trim() && !subjectError && !messageError;

  const onSubmit = async (e) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      const isTargetNone = target === TARGET_NONE;
      const result = await createInquiry({
        subject: subjectWithPrefix,
        message: message.trim(),
        targetType: isTargetNone ? null : 'sales',
        targetId: isTargetNone ? null : Number(target),
      });
      navigate(`/mypage/inquiries/${result.id}`);
    } catch (e) {
      setError(e?.response?.data?.message || '送信に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const formatPurchaseLabel = (p) => {
    const parts = [];
    if (p.salesDate) parts.push(`[${p.salesDate}]`);
    parts.push(p.productName ?? '商品');
    const variant = [p.color, p.size].filter(Boolean).join(' / ');
    if (variant) parts.push(`（${variant}）`);
    return parts.join(' ');
  };

  return (
    <Container maxWidth="sm" sx={{ py: 4 }}>
      <Typography variant="h5" mb={2}>新規問い合わせ</Typography>

      <Paper sx={{ p: 3 }}>
        <form onSubmit={onSubmit}>
          <Stack spacing={2}>
            <TextField
              select
              label="お問い合わせ種別"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              helperText="件名の先頭にカテゴリラベルが自動で付与されます"
            >
              {CATEGORY_OPTIONS.map((o) => (
                <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
              ))}
            </TextField>
            <TextField
              label="件名"
              required
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              error={!!subjectError}
              helperText={subjectError || `${subjectWithPrefix.length} / ${SUBJECT_MAX}`}
              inputProps={{ maxLength: SUBJECT_MAX + 10 }}
            />
            <TextField
              select
              label="関連する購入履歴（任意）"
              value={target}
              onChange={(e) => setTarget(e.target.value)}
              helperText="関連する商品・注文がある場合は選択してください"
            >
              <MenuItem value={TARGET_NONE}>選択しない</MenuItem>
              {purchases.map((p) => (
                <MenuItem key={p.salesId} value={String(p.salesId)}>
                  {formatPurchaseLabel(p)}
                </MenuItem>
              ))}
            </TextField>
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
