import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SortSelect, { sortProducts } from './SortSelect';

describe('SortSelect', () => {
  it('4つの並び替え選択肢が表示される', async () => {
    const user = userEvent.setup();
    render(<SortSelect value="recommended" onChange={() => {}} />);
    await user.click(screen.getByLabelText('並び替え'));
    expect(await screen.findByRole('option', { name: 'おすすめ順' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '価格の安い順' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '価格の高い順' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '新着順' })).toBeInTheDocument();
  });

  it('選択変更で onChange が呼ばれる', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<SortSelect value="recommended" onChange={onChange} />);
    await user.click(screen.getByLabelText('並び替え'));
    await user.click(await screen.findByRole('option', { name: '価格の安い順' }));
    expect(onChange).toHaveBeenCalledWith('priceAsc');
  });
});

describe('sortProducts', () => {
  const items = [
    { productId: 1, minPrice: 300, createdAt: '2026-01-03' },
    { productId: 2, minPrice: 100, createdAt: '2026-01-01' },
    { productId: 3, minPrice: 200, createdAt: '2026-01-05' },
  ];

  it('priceAsc は minPrice 昇順', () => {
    expect(sortProducts(items, 'priceAsc').map((p) => p.productId)).toEqual([2, 3, 1]);
  });

  it('priceDesc は minPrice 降順', () => {
    expect(sortProducts(items, 'priceDesc').map((p) => p.productId)).toEqual([1, 3, 2]);
  });

  it('newest は createdAt 降順', () => {
    expect(sortProducts(items, 'newest').map((p) => p.productId)).toEqual([3, 1, 2]);
  });

  it('recommended は元の順序を維持', () => {
    expect(sortProducts(items, 'recommended').map((p) => p.productId)).toEqual([1, 2, 3]);
  });

  it('元の配列を破壊しない', () => {
    const ids = items.map((p) => p.productId);
    sortProducts(items, 'priceDesc');
    expect(items.map((p) => p.productId)).toEqual(ids);
  });
});
