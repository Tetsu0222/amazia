import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ProductList from './ProductList';
import * as api from '../api/products';

vi.mock('../api/products');

function renderProductList() {
  return render(
    <MemoryRouter>
      <ProductList />
    </MemoryRouter>
  );
}

describe('ProductList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('ローディング中はスピナーを表示する', () => {
    api.getMarketProducts.mockReturnValue(new Promise(() => {}));
    renderProductList();
    expect(document.querySelector('.MuiCircularProgress-root')).toBeInTheDocument();
  });

  it('商品一覧を取得して表示する', async () => {
    api.getMarketProducts.mockResolvedValue([
      { productId: 1, productName: 'テスト商品A', minPrice: 1000, totalStock: 5, preorderStatus: 'ON_SALE' },
      { productId: 2, productName: 'テスト商品B', minPrice: 2000, totalStock: 0, preorderStatus: 'SOLD_OUT' },
    ]);
    renderProductList();

    await waitFor(() => {
      expect(screen.getByText('テスト商品A')).toBeInTheDocument();
      expect(screen.getByText('テスト商品B')).toBeInTheDocument();
    });
  });

  it('API エラー時にエラーメッセージを表示する', async () => {
    api.getMarketProducts.mockRejectedValue(new Error('network error'));
    renderProductList();

    await waitFor(() => {
      expect(screen.getByText('商品データの取得に失敗しました')).toBeInTheDocument();
    });
  });

  it('商品が 0 件のとき「現在表示できる商品がありません」を表示する', async () => {
    api.getMarketProducts.mockResolvedValue([]);
    renderProductList();

    await waitFor(() => {
      expect(screen.getByText('現在表示できる商品がありません。')).toBeInTheDocument();
    });
  });

  it('価格を ¥1,000 〜 の形式で表示する', async () => {
    api.getMarketProducts.mockResolvedValue([
      { productId: 1, productName: '価格テスト', minPrice: 1000, totalStock: 1, preorderStatus: 'ON_SALE' },
    ]);
    renderProductList();

    await waitFor(() => {
      expect(screen.getByText('¥1,000 〜')).toBeInTheDocument();
    });
  });

  // フェーズ14.5 §4-2: ステータス別ラベルと補足表示
  it('ON_SALE は「通常販売」ラベルと在庫数を表示する', async () => {
    api.getMarketProducts.mockResolvedValue([
      { productId: 1, productName: '通常商品', minPrice: 1000, totalStock: 7, preorderStatus: 'ON_SALE' },
    ]);
    renderProductList();
    await waitFor(() => {
      expect(screen.getByText('通常販売')).toBeInTheDocument();
      expect(screen.getByText('在庫：7 個')).toBeInTheDocument();
    });
  });

  it('PRE_ORDER は「予約受付中」ラベルと発売日を表示する', async () => {
    api.getMarketProducts.mockResolvedValue([
      {
        productId: 2, productName: '予約商品', minPrice: 2000, totalStock: 0,
        preorderStatus: 'PRE_ORDER', releaseDate: '2026-08-01', preorderStartDate: '2026-07-01',
      },
    ]);
    renderProductList();
    await waitFor(() => {
      expect(screen.getByText('予約受付中')).toBeInTheDocument();
      expect(screen.getByText('発売日：2026-08-01')).toBeInTheDocument();
      // ON_SALE 以外は在庫数（「在庫：N 個」）を表示しない
      expect(screen.queryByText(/在庫：\d+ 個/)).not.toBeInTheDocument();
    });
  });

  it('PRE_ORDER_NOT_STARTED は「予約開始前」ラベルと予約開始日を表示する', async () => {
    api.getMarketProducts.mockResolvedValue([
      {
        productId: 3, productName: '予約前商品', minPrice: 3000, totalStock: 0,
        preorderStatus: 'PRE_ORDER_NOT_STARTED', preorderStartDate: '2026-09-01',
      },
    ]);
    renderProductList();
    await waitFor(() => {
      expect(screen.getByText('予約開始前')).toBeInTheDocument();
      expect(screen.getByText('予約開始：2026-09-01')).toBeInTheDocument();
    });
  });

  it('BACK_ORDER は「再入荷予約受付中」ラベルと在庫切れ表示が出る', async () => {
    api.getMarketProducts.mockResolvedValue([
      {
        productId: 4, productName: '再入荷待ち', minPrice: 4000, totalStock: 0,
        preorderStatus: 'BACK_ORDER',
      },
    ]);
    renderProductList();
    await waitFor(() => {
      expect(screen.getByText('再入荷予約受付中')).toBeInTheDocument();
      expect(screen.getByText(/在庫切れ/)).toBeInTheDocument();
    });
  });

  it('SOLD_OUT は「完売」ラベルが表示される', async () => {
    api.getMarketProducts.mockResolvedValue([
      { productId: 5, productName: '完売商品', minPrice: 5000, totalStock: 0, preorderStatus: 'SOLD_OUT' },
    ]);
    renderProductList();
    await waitFor(() => {
      expect(screen.getByText('完売')).toBeInTheDocument();
    });
  });
});
