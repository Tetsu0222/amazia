import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ProductCard from './ProductCard';

const navigateMock = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => navigateMock };
});

vi.mock('../../customer/context/useAuth', () => ({
  useAuth: () => ({ isAuthenticated: false, customer: null, logout: vi.fn() }),
}));

function renderCard(product) {
  return render(
    <MemoryRouter>
      <ProductCard product={product} />
    </MemoryRouter>
  );
}

describe('ProductCard', () => {
  beforeEach(() => navigateMock.mockClear());

  it('商品名・価格・「カートに入れる」ボタンが描画される', () => {
    renderCard({
      productId: 1, productName: 'テスト商品', minPrice: 1500,
      totalStock: 3, preorderStatus: 'ON_SALE', skuCount: 1,
    });
    expect(screen.getByText('テスト商品')).toBeInTheDocument();
    expect(screen.getByText('¥1,500')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'カートに入れる' })).toBeInTheDocument();
  });

  it('ON_SALE 商品は「残りN点」を表示する', () => {
    renderCard({
      productId: 1, productName: 'a', minPrice: 100,
      totalStock: 5, preorderStatus: 'ON_SALE', skuCount: 1,
    });
    expect(screen.getByText('残り5点')).toBeInTheDocument();
  });

  it('PRE_ORDER 商品は「発売日：YYYY-MM-DD」を表示する', () => {
    renderCard({
      productId: 1, productName: 'a', minPrice: 100,
      totalStock: 0, preorderStatus: 'PRE_ORDER', releaseDate: '2026-08-01', skuCount: 1,
    });
    expect(screen.getByText('発売日：2026-08-01')).toBeInTheDocument();
  });

  it('カードクリックで詳細ページに遷移する', async () => {
    const user = userEvent.setup();
    renderCard({
      productId: 42, productName: 'クリック商品', minPrice: 100,
      totalStock: 1, preorderStatus: 'ON_SALE', skuCount: 1,
    });
    await user.click(screen.getByText('クリック商品'));
    expect(navigateMock).toHaveBeenCalledWith('/products/42');
  });

  it('SKU が複数のとき「選択して購入」ボタンを表示する', () => {
    renderCard({
      productId: 1, productName: 'a', minPrice: 100,
      totalStock: 5, preorderStatus: 'ON_SALE', skuCount: 3,
    });
    expect(screen.getByRole('button', { name: '選択して購入' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'カートに入れる' })).not.toBeInTheDocument();
  });

  it('在庫切れ・SOLD_OUT・NOT_STARTED 系は「カートに入れる」が非活性', () => {
    renderCard({
      productId: 1, productName: 'a', minPrice: 100,
      totalStock: 0, preorderStatus: 'SOLD_OUT', skuCount: 1,
    });
    expect(screen.getByRole('button', { name: 'カートに入れる' })).toBeDisabled();
  });
});
