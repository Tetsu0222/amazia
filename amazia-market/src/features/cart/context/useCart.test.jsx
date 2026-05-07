import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { CartProvider } from './CartContext';
import { useCart } from './useCart';
import * as api from '../api/cart';

vi.mock('../api/cart');

vi.mock('../../customer/context/useAuth', () => ({
  useAuth: () => ({ isAuthenticated: true, customer: { id: 1 }, logout: vi.fn() }),
}));

const wrapper = ({ children }) => <CartProvider>{children}</CartProvider>;

const emptyCart = { cartId: null, items: [], totalCount: 0, totalPrice: 0 };

describe('useCart / CartProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getMyCart.mockResolvedValue(emptyCart);
  });

  it('初期状態は空（items=[]・totalCount=0）', async () => {
    const { result } = renderHook(() => useCart(), { wrapper });
    await waitFor(() => expect(api.getMyCart).toHaveBeenCalled());
    expect(result.current.items).toEqual([]);
    expect(result.current.totalCount).toBe(0);
  });

  it('addToCart 後にアイテムが反映される', async () => {
    api.addToCart.mockResolvedValue({
      cartId: 1, totalCount: 2, totalPrice: 6000,
      items: [{ itemId: 10, skuId: 5, quantity: 2, subtotal: 6000, productName: 'A', color: '赤', size: 'M', unitPrice: 3000, availableStock: 8, productId: 2, preorder: false }],
    });

    const { result } = renderHook(() => useCart(), { wrapper });
    await waitFor(() => expect(api.getMyCart).toHaveBeenCalled());

    await act(async () => {
      await result.current.addToCart(5, 2, false);
    });

    expect(api.addToCart).toHaveBeenCalledWith(5, 2, false);
    expect(result.current.totalCount).toBe(2);
    expect(result.current.items.length).toBe(1);
  });

  it('removeFromCart でアイテムが消える', async () => {
    api.removeFromCart.mockResolvedValue(emptyCart);

    const { result } = renderHook(() => useCart(), { wrapper });
    await waitFor(() => expect(api.getMyCart).toHaveBeenCalled());

    await act(async () => {
      await result.current.removeFromCart(10);
    });

    expect(api.removeFromCart).toHaveBeenCalledWith(10);
    expect(result.current.items).toEqual([]);
  });

  it('clearCart で空になる', async () => {
    api.clearCart.mockResolvedValue();

    const { result } = renderHook(() => useCart(), { wrapper });
    await waitFor(() => expect(api.getMyCart).toHaveBeenCalled());

    await act(async () => {
      await result.current.clearCart();
    });

    expect(api.clearCart).toHaveBeenCalled();
    expect(result.current.items).toEqual([]);
    expect(result.current.totalCount).toBe(0);
  });
});

describe('useCart Provider 外（フォールバック）', () => {
  it('Provider が無くても空カートを返す', () => {
    const { result } = renderHook(() => useCart());
    expect(result.current.items).toEqual([]);
    expect(result.current.totalCount).toBe(0);
  });
});
