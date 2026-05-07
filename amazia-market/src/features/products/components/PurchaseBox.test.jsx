import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import PurchaseBox from './PurchaseBox';
import { getPreorderStatusMeta } from '../preorderStatus';

const navigateMock = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => navigateMock };
});

let mockAuthValue = { isAuthenticated: true, customer: { id: 1 }, initializing: false };
vi.mock('../../customer/context/useAuth', () => ({
  useAuth: () => mockAuthValue,
}));

const addToCartMock = vi.fn();
vi.mock('../../cart/context/useCart', () => ({
  useCart: () => ({
    items: [], totalCount: 0, totalPrice: 0,
    addToCart: addToCartMock,
    updateQuantity: vi.fn(), removeFromCart: vi.fn(), clearCart: vi.fn(), refresh: vi.fn(),
  }),
}));

function renderBox(props) {
  return render(
    <MemoryRouter>
      <PurchaseBox {...props} />
    </MemoryRouter>
  );
}

const onSaleSku = { skuId: 101, color: '赤', size: 'M', price: 3000, stock: 8 };

describe('PurchaseBox', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthValue = { isAuthenticated: true, customer: { id: 1 }, initializing: false };
  });

  it('selectedSku が null のときは何も描画しない', () => {
    const { container } = renderBox({
      productId: '1', selectedSku: null,
      preorderStatus: 'ON_SALE', statusMeta: getPreorderStatusMeta('ON_SALE'),
    });
    expect(container.firstChild).toBeNull();
  });

  it('数量セレクトで 1〜10 が選べる（在庫が十分なとき）', async () => {
    const user = userEvent.setup();
    renderBox({
      productId: '1',
      selectedSku: { ...onSaleSku, stock: 20 },
      preorderStatus: 'ON_SALE',
      statusMeta: getPreorderStatusMeta('ON_SALE'),
    });
    await user.click(screen.getByLabelText('数量'));
    expect(await screen.findByRole('option', { name: '1' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '10' })).toBeInTheDocument();
    // 11 はない
    expect(screen.queryByRole('option', { name: '11' })).not.toBeInTheDocument();
  });

  it('「カートに入れる」で addToCart が選択数量で呼ばれる', async () => {
    const user = userEvent.setup();
    addToCartMock.mockResolvedValue();
    renderBox({
      productId: '1', selectedSku: onSaleSku,
      preorderStatus: 'ON_SALE', statusMeta: getPreorderStatusMeta('ON_SALE'),
    });
    await user.click(screen.getByLabelText('数量'));
    await user.click(await screen.findByRole('option', { name: '3' }));
    await user.click(screen.getByRole('button', { name: 'カートに入れる' }));
    expect(addToCartMock).toHaveBeenCalledWith(101, 3, false);
  });

  it('「今すぐ買う」で /checkout?product_id=&sku_id=&quantity= に遷移', async () => {
    const user = userEvent.setup();
    renderBox({
      productId: '5', selectedSku: onSaleSku,
      preorderStatus: 'ON_SALE', statusMeta: getPreorderStatusMeta('ON_SALE'),
    });
    await user.click(screen.getByRole('button', { name: '今すぐ買う' }));
    expect(navigateMock).toHaveBeenCalledWith('/checkout?product_id=5&sku_id=101&quantity=1');
  });

  it('在庫切れ ON_SALE はカートボタン非表示・「今すぐ買う」非活性', () => {
    renderBox({
      productId: '1',
      selectedSku: { ...onSaleSku, stock: 0 },
      preorderStatus: 'ON_SALE',
      statusMeta: getPreorderStatusMeta('ON_SALE'),
    });
    expect(screen.queryByRole('button', { name: 'カートに入れる' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '今すぐ買う' })).toBeDisabled();
  });

  it('PRE_ORDER は「今すぐ予約」表示、遷移時に &preorder=1 が付く', async () => {
    const user = userEvent.setup();
    renderBox({
      productId: '7',
      selectedSku: { ...onSaleSku, stock: 0 },
      preorderStatus: 'PRE_ORDER',
      statusMeta: getPreorderStatusMeta('PRE_ORDER'),
    });
    await user.click(screen.getByRole('button', { name: '今すぐ予約' }));
    expect(navigateMock).toHaveBeenCalledWith('/checkout?product_id=7&sku_id=101&quantity=1&preorder=1');
  });

  it('未ログインでカート追加すると /login へリダイレクトされる', async () => {
    mockAuthValue = { isAuthenticated: false, customer: null, initializing: false };
    const user = userEvent.setup();
    renderBox({
      productId: '1', selectedSku: onSaleSku,
      preorderStatus: 'ON_SALE', statusMeta: getPreorderStatusMeta('ON_SALE'),
    });
    await user.click(screen.getByRole('button', { name: 'カートに入れる' }));
    expect(navigateMock).toHaveBeenCalledWith('/login', { state: { from: '/products/1' } });
    expect(addToCartMock).not.toHaveBeenCalled();
  });
});
