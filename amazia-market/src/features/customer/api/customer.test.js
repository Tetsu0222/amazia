import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';

vi.mock('axios');

const interceptorHandlers = [];
const mockClient = {
  get: vi.fn(),
  post: vi.fn(),
  interceptors: {
    request: {
      use: vi.fn((fn) => { interceptorHandlers.push(fn); }),
    },
  },
};
axios.create.mockReturnValue(mockClient);

const {
  registerCustomer,
  loginCustomer,
  logoutCustomer,
  getMyPage,
  checkEmailAvailability,
  searchPostalAddresses,
  requestPasswordReset,
  confirmPasswordReset,
  setCsrfToken,
} = await import('./customer.js');

describe('customer API client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setCsrfToken(null);
  });

  it('registerCustomer は /register を POST する', async () => {
    mockClient.post.mockResolvedValue({ data: { id: 1 } });
    const payload = { email: 'a@b.com', password: 'Abc12345' };
    const r = await registerCustomer(payload);
    expect(mockClient.post).toHaveBeenCalledWith('/register', payload);
    expect(r).toEqual({ id: 1 });
  });

  it('loginCustomer は /login を POST して csrfToken を含むレスポンスを返す', async () => {
    mockClient.post.mockResolvedValue({ data: { customerId: 1, email: 'a@b.com', csrfToken: 'tok' } });
    const r = await loginCustomer({ email: 'a@b.com', password: 'pw' });
    expect(mockClient.post).toHaveBeenCalledWith('/login', { email: 'a@b.com', password: 'pw' });
    expect(r.csrfToken).toBe('tok');
  });

  it('logoutCustomer は /logout を POST する', async () => {
    mockClient.post.mockResolvedValue({ data: { success: true } });
    await logoutCustomer();
    expect(mockClient.post).toHaveBeenCalledWith('/logout');
  });

  it('getMyPage は /me を GET する', async () => {
    mockClient.get.mockResolvedValue({ data: { email: 'a@b.com' } });
    await getMyPage();
    expect(mockClient.get).toHaveBeenCalledWith('/me');
  });

  it('checkEmailAvailability は email クエリ付きで /email-availability を GET する', async () => {
    mockClient.get.mockResolvedValue({ data: { available: true } });
    await checkEmailAvailability('a@b.com');
    expect(mockClient.get).toHaveBeenCalledWith('/email-availability', { params: { email: 'a@b.com' } });
  });

  it('searchPostalAddresses は postal_code クエリで /postal-addresses を GET する', async () => {
    mockClient.get.mockResolvedValue({ data: [] });
    await searchPostalAddresses('1000001');
    expect(mockClient.get).toHaveBeenCalledWith('/postal-addresses', { params: { postal_code: '1000001' } });
  });

  it('requestPasswordReset は /password/reset を POST する', async () => {
    mockClient.post.mockResolvedValue({ data: {} });
    await requestPasswordReset('a@b.com');
    expect(mockClient.post).toHaveBeenCalledWith('/password/reset', { email: 'a@b.com' });
  });

  it('confirmPasswordReset は /password/reset/confirm を POST する', async () => {
    mockClient.post.mockResolvedValue({ data: {} });
    await confirmPasswordReset('xyz', 'NewPass123');
    expect(mockClient.post).toHaveBeenCalledWith('/password/reset/confirm', { token: 'xyz', newPassword: 'NewPass123' });
  });

  it('CSRF 未設定時は X-CSRF-Token を付けない', () => {
    setCsrfToken(null);
    const config = { method: 'post', headers: {} };
    const handler = interceptorHandlers[0];
    const result = handler(config);
    expect(result.headers['X-CSRF-Token']).toBeUndefined();
  });

  it('CSRF 設定時、POST には X-CSRF-Token ヘッダが付与される', () => {
    setCsrfToken('mytoken');
    const config = { method: 'post', headers: {} };
    const handler = interceptorHandlers[0];
    const result = handler(config);
    expect(result.headers['X-CSRF-Token']).toBe('mytoken');
  });

  it('CSRF 設定時でも GET には X-CSRF-Token を付けない', () => {
    setCsrfToken('mytoken');
    const config = { method: 'get', headers: {} };
    const handler = interceptorHandlers[0];
    const result = handler(config);
    expect(result.headers['X-CSRF-Token']).toBeUndefined();
  });
});
