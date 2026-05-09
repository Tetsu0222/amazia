import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { listMyInquiries } from '../api/inquiry';

const STATUS_LABELS = { NEW: '未対応', IN_PROGRESS: '対応中', DONE: '完了' };
const STATUS_COLORS = { NEW: 'error', IN_PROGRESS: 'info', DONE: 'default' };

export default function MyPageInquiryList() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const data = await listMyInquiries({ page: 0, size: 20 });
        if (!alive) return;
        setRows(data.content || []);
      } catch (e) {
        if (alive) setError(e?.response?.data?.message || '問い合わせの取得に失敗しました');
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => { alive = false; };
  }, []);

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        alignItems={{ xs: 'flex-start', sm: 'center' }}
        spacing={2}
        mb={3}
        sx={{ width: '100%' }}
      >
        <Typography variant="h5" sx={{ flexGrow: 1 }}>問い合わせ一覧</Typography>
        <Button
          component={Link}
          to="/mypage/inquiries/new"
          variant="contained"
          startIcon={<AddIcon />}
        >
          新規問い合わせ
        </Button>
      </Stack>

      {loading && <CircularProgress />}
      {error && <Typography color="error">{error}</Typography>}
      {!loading && !error && (
        <Paper>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>件名</TableCell>
                  <TableCell width={120}>ステータス</TableCell>
                  <TableCell width={180}>最終更新</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3} align="center">
                      <Box py={3} color="text.secondary">問い合わせはまだありません</Box>
                    </TableCell>
                  </TableRow>
                )}
                {rows.map((r) => (
                  <TableRow key={r.id} hover>
                    <TableCell>
                      <Link to={`/mypage/inquiries/${r.id}`}>{r.subject}</Link>
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={STATUS_LABELS[r.status] || r.status}
                        color={STATUS_COLORS[r.status] || 'default'}
                      />
                    </TableCell>
                    <TableCell>{new Date(r.updatedAt).toLocaleString('ja-JP')}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}
    </Container>
  );
}
