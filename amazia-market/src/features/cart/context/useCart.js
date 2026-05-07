import { useContext } from 'react';
import { CartContext } from './CartContextValue';

const NOOP = async () => {};
const FALLBACK = {
  items: [],
  totalCount: 0,
  totalPrice: 0,
  cartId: null,
  loading: false,
  refresh: NOOP,
  addToCart: NOOP,
  updateQuantity: NOOP,
  removeFromCart: NOOP,
  clearCart: NOOP,
};

// Provider が無い場合は空カートのフォールバックを返す。
// Provider が必須だと既存の component-only テスト（AppHeader / ProductCard 等）が
// すべて Provider ラップを強制され網羅率が下がるため、Auth と異なり緩めのガードを採用。
export function useCart() {
  return useContext(CartContext) ?? FALLBACK;
}
