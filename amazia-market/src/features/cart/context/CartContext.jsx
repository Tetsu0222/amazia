import { useCallback, useEffect, useState } from 'react';
import {
  getMyCart,
  addToCart as apiAdd,
  updateQuantity as apiUpdate,
  removeFromCart as apiRemove,
  clearCart as apiClear,
} from '../api/cart';
import { useAuth } from '../../customer/context/useAuth';
import { CartContext } from './CartContextValue';

const EMPTY_CART = { cartId: null, items: [], totalCount: 0, totalPrice: 0 };

export function CartProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const [cart, setCart] = useState(EMPTY_CART);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!isAuthenticated) {
      setCart(EMPTY_CART);
      return EMPTY_CART;
    }
    setLoading(true);
    try {
      const data = await getMyCart();
      setCart(data ?? EMPTY_CART);
      return data;
    } catch {
      setCart(EMPTY_CART);
      return EMPTY_CART;
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const addToCart = useCallback(async (skuId, quantity, preorder = false) => {
    const data = await apiAdd(skuId, quantity, preorder);
    setCart(data);
    return data;
  }, []);

  const updateQuantity = useCallback(async (itemId, quantity) => {
    const data = await apiUpdate(itemId, quantity);
    setCart(data);
    return data;
  }, []);

  const removeFromCart = useCallback(async (itemId) => {
    const data = await apiRemove(itemId);
    setCart(data);
    return data;
  }, []);

  const clearCart = useCallback(async () => {
    await apiClear();
    setCart(EMPTY_CART);
  }, []);

  const value = {
    items: cart.items,
    totalCount: cart.totalCount,
    totalPrice: cart.totalPrice,
    cartId: cart.cartId,
    loading,
    refresh,
    addToCart,
    updateQuantity,
    removeFromCart,
    clearCart,
  };

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}
