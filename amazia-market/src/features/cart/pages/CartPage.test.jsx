import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import CartPage from './CartPage';

const navigateMock = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => navigateMock };
});

const useCartMock = vi.fn();
vi.mock('../context/useCart', () => ({
  useCart: () => useCartMock(),
}));

function renderPage() {
  return render(
    <MemoryRouter>
      <CartPage />
    </MemoryRouter>
  );
}

const sampleItem = {
  itemId: 10,
  skuId: 5,
  productId: 2,
  productName: 'テスト商品',
  color: '赤',
  size: 'M',
  unitPrice: 3000,
  quantity: 2,
  subtotal: 6000,
  availableStock: 8,
  preorder: false,
};

describe('CartPage', () => {
  beforeEach(() => {
    navigateMock.mockClear();
  });

  it('空カート時は「カートは空です」を表示する', () => {
    useCartMock.mockReturnValue({
      items: [], totalCount: 0, totalPrice: 0, loading: false,
      updateQuantity: vi.fn(), removeFromCart: vi.fn(),
    });
    renderPage();
    expect(screen.getByText('カートは空です')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '商品一覧に戻る' })).toBeInTheDocument();
  });

  it('アイテムが表示され合計と「レジに進む」が出る', () => {
    useCartMock.mockReturnValue({
      items: [sampleItem], totalCount: 2, totalPrice: 6000, loading: false,
      updateQuantity: vi.fn(), removeFromCart: vi.fn(),
    });
    renderPage();
    expect(screen.getByText('テスト商品')).toBeInTheDocument();
    // 小計と合計の2箇所に ¥6,000 が出る
    expect(screen.getAllByText('¥6,000').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole('button', { name: 'レジに進む（2 点）' })).toBeInTheDocument();
  });

  it('「レジに進む」で /checkout?from=cart に遷移する', async () => {
    const user = userEvent.setup();
    useCartMock.mockReturnValue({
      items: [sampleItem], totalCount: 2, totalPrice: 6000, loading: false,
      updateQuantity: vi.fn(), removeFromCart: vi.fn(),
    });
    renderPage();
    await user.click(screen.getByRole('button', { name: 'レジに進む（2 点）' }));
    expect(navigateMock).toHaveBeenCalledWith('/checkout?from=cart');
  });

  it('削除ボタンで removeFromCart が呼ばれる', async () => {
    const user = userEvent.setup();
    const removeFromCart = vi.fn().mockResolvedValue();
    useCartMock.mockReturnValue({
      items: [sampleItem], totalCount: 2, totalPrice: 6000, loading: false,
      updateQuantity: vi.fn(), removeFromCart,
    });
    renderPage();
    await user.click(screen.getByRole('button', { name: '削除（テスト商品）' }));
    await waitFor(() => expect(removeFromCart).toHaveBeenCalledWith(10));
  });

  it('数量変更で updateQuantity が呼ばれる', async () => {
    const user = userEvent.setup();
    const updateQuantity = vi.fn().mockResolvedValue();
    useCartMock.mockReturnValue({
      items: [sampleItem], totalCount: 2, totalPrice: 6000, loading: false,
      updateQuantity, removeFromCart: vi.fn(),
    });
    renderPage();
    await user.click(screen.getByLabelText('数量（テスト商品）'));
    await user.click(await screen.findByRole('option', { name: '5' }));
    await waitFor(() => expect(updateQuantity).toHaveBeenCalledWith(10, 5));
  });
});
