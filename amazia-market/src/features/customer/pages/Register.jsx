import { useState, useEffect, useMemo } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import {
  Container, Paper, Stack, TextField, Button, Typography,
  Alert, MenuItem, Link, FormHelperText,
} from '@mui/material';
import {
  registerCustomer,
  checkEmailAvailability,
  searchPostalAddresses,
} from '../api/customer';

const PAYMENT_METHODS = [
  { value: 'credit_card', label: 'クレジットカード' },
  { value: 'bank_transfer', label: '銀行振込' },
  { value: 'other', label: 'その他' },
];

const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;
const POSTAL_REGEX = /^\d{7}$/;

function validatePassword(pw) {
  if (!pw) return 'パスワードを入力してください';
  if (!PASSWORD_REGEX.test(pw)) return '8文字以上で英大文字・英小文字・数字を含めてください';
  return null;
}

function calcAge(birthday, today = new Date()) {
  if (!birthday) return null;
  const d = new Date(birthday);
  if (Number.isNaN(d.getTime())) return null;
  let age = today.getFullYear() - d.getFullYear();
  const m = today.getMonth() - d.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < d.getDate())) age--;
  return age;
}

export default function Register() {
  const navigate = useNavigate();

  const [form, setForm] = useState({
    nameLast: '',
    nameFirst: '',
    postalCode: '',
    address: '',
    birthday: '',
    email: '',
    password: '',
    passwordConfirm: '',
    paymentMethod: 'credit_card',
    cardToken: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  const [emailStatus, setEmailStatus] = useState({ checking: false, available: null });
  const [postalCandidates, setPostalCandidates] = useState([]);
  const [postalError, setPostalError] = useState(null);

  const handleChange = (field) => (e) => {
    setForm((f) => ({ ...f, [field]: e.target.value }));
  };

  // 郵便番号 debounce 検索
  const normalizedPostal = form.postalCode.replace(/-/g, '');
  const postalValid = POSTAL_REGEX.test(normalizedPostal);
  useEffect(() => {
    let canceled = false;
    if (!postalValid) {
      const timer = setTimeout(() => {
        if (canceled) return;
        setPostalCandidates([]);
        setPostalError(null);
      }, 0);
      return () => { canceled = true; clearTimeout(timer); };
    }
    const timer = setTimeout(async () => {
      try {
        const list = await searchPostalAddresses(normalizedPostal);
        if (canceled) return;
        setPostalCandidates(list);
        setPostalError(list.length === 0 ? '住所が取得できませんでした' : null);
        if (list.length === 1) {
          const a = list[0];
          setForm((f) => ({ ...f, address: `${a.prefecture}${a.city}${a.town}` }));
        }
      } catch {
        if (canceled) return;
        setPostalCandidates([]);
        setPostalError('住所が取得できませんでした');
      }
    }, 300);
    return () => { canceled = true; clearTimeout(timer); };
  }, [normalizedPostal, postalValid]);

  // メール重複の Ajax 事前チェック（debounce）
  const emailTrimmed = form.email.trim();
  const emailValid = /.+@.+\..+/.test(emailTrimmed);
  useEffect(() => {
    let canceled = false;
    if (!emailValid) {
      const timer = setTimeout(() => {
        if (canceled) return;
        setEmailStatus({ checking: false, available: null });
      }, 0);
      return () => { canceled = true; clearTimeout(timer); };
    }
    const startTimer = setTimeout(() => {
      if (canceled) return;
      setEmailStatus({ checking: true, available: null });
    }, 0);
    const timer = setTimeout(async () => {
      try {
        const res = await checkEmailAvailability(emailTrimmed);
        if (canceled) return;
        setEmailStatus({ checking: false, available: res.available });
      } catch {
        if (canceled) return;
        setEmailStatus({ checking: false, available: null });
      }
    }, 400);
    return () => { canceled = true; clearTimeout(startTimer); clearTimeout(timer); };
  }, [emailTrimmed, emailValid]);

  const passwordError = useMemo(() => validatePassword(form.password), [form.password]);
  const passwordMismatch = form.passwordConfirm && form.password !== form.passwordConfirm;
  const age = calcAge(form.birthday);
  const ageError = age != null && age < 18 ? '18歳未満は登録できません' : null;

  const formInvalid =
    !form.nameLast ||
    !form.nameFirst ||
    !POSTAL_REGEX.test(form.postalCode.replace(/-/g, '')) ||
    !form.address ||
    !form.birthday ||
    !form.email ||
    emailStatus.available === false ||
    passwordError ||
    passwordMismatch ||
    ageError;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (formInvalid) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await registerCustomer({
        ...form,
        postalCode: form.postalCode.replace(/-/g, ''),
        cardToken: form.paymentMethod === 'credit_card' ? (form.cardToken || 'mock_token') : null,
      });
      navigate('/login', { state: { registered: true } });
    } catch (err) {
      const status = err?.response?.status;
      if (status === 409) {
        setSubmitError('このメールアドレスは既に登録されています');
      } else if (status === 400) {
        setSubmitError('入力内容に誤りがあります。各項目をご確認ください。');
      } else {
        setSubmitError('登録に失敗しました。時間をおいて再度お試しください。');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Container maxWidth="sm" sx={{ mt: 4, mb: 6 }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h5" gutterBottom>会員登録</Typography>
        <form onSubmit={handleSubmit} noValidate>
          <Stack spacing={2}>
            {submitError && <Alert severity="error">{submitError}</Alert>}
            <Stack direction="row" spacing={2}>
              <TextField
                label="姓" required fullWidth
                value={form.nameLast} onChange={handleChange('nameLast')}
                slotProps={{ htmlInput: { maxLength: 100 } }}
              />
              <TextField
                label="名" required fullWidth
                value={form.nameFirst} onChange={handleChange('nameFirst')}
                slotProps={{ htmlInput: { maxLength: 100 } }}
              />
            </Stack>
            <TextField
              label="郵便番号（7桁）" required
              value={form.postalCode} onChange={handleChange('postalCode')}
              error={!!postalError}
              helperText={postalError ?? '入力すると住所を自動で補完します'}
              slotProps={{ htmlInput: { maxLength: 8 } }}
            />
            {postalCandidates.length > 1 && (
              <TextField
                select label="住所候補から選択"
                onChange={(e) => setForm((f) => ({ ...f, address: e.target.value }))}
              >
                {postalCandidates.map((a, i) => (
                  <MenuItem
                    key={i}
                    value={`${a.prefecture}${a.city}${a.town}`}
                  >
                    {a.prefecture}{a.city}{a.town}
                  </MenuItem>
                ))}
              </TextField>
            )}
            <TextField
              label="住所（建物名含む）" required
              value={form.address} onChange={handleChange('address')}
              slotProps={{ htmlInput: { maxLength: 255 } }}
            />
            <TextField
              label="生年月日" type="date" required
              value={form.birthday} onChange={handleChange('birthday')}
              error={!!ageError}
              helperText={ageError}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="メールアドレス" type="email" required
              value={form.email} onChange={handleChange('email')}
              error={emailStatus.available === false}
              helperText={
                emailStatus.checking
                  ? '確認中…'
                  : emailStatus.available === false
                    ? 'このメールアドレスは既に登録されています'
                    : emailStatus.available === true
                      ? '使用できます'
                      : ' '
              }
              slotProps={{ htmlInput: { maxLength: 255 } }}
            />
            <TextField
              label="パスワード" type="password" required
              value={form.password} onChange={handleChange('password')}
              error={!!passwordError}
              helperText={passwordError ?? '8文字以上・英大小文字・数字を含む'}
            />
            <TextField
              label="パスワード（確認）" type="password" required
              value={form.passwordConfirm} onChange={handleChange('passwordConfirm')}
              error={!!passwordMismatch}
              helperText={passwordMismatch ? 'パスワードが一致しません' : ' '}
            />
            <TextField
              select label="決済方法" required
              value={form.paymentMethod} onChange={handleChange('paymentMethod')}
            >
              {PAYMENT_METHODS.map((m) => (
                <MenuItem key={m.value} value={m.value}>{m.label}</MenuItem>
              ))}
            </TextField>
            {form.paymentMethod === 'credit_card' && (
              <FormHelperText>
                ※ 本フェーズではモックトークン（mock_token）で登録されます
              </FormHelperText>
            )}
            <Button type="submit" variant="contained" disabled={submitting || formInvalid}>
              {submitting ? '登録中…' : '登録する'}
            </Button>
            <Link component={RouterLink} to="/login" variant="body2">
              既にアカウントをお持ちの方はこちら
            </Link>
          </Stack>
        </form>
      </Paper>
    </Container>
  );
}
