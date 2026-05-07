import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ProductList from './ProductList';
import * as api from '../api/products';

vi.mock('../api/products');

vi.mock('../../customer/context/useAuth', () => ({
  useAuth: () => ({ isAuthenticated: false, customer: null, logout: vi.fn() }),
}));

function renderProductList(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
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

  it('ON_SALE 商品の価格を ¥1,000 形式で表示する（「〜」なし）', async () => {
    api.getMarketProducts.mockResolvedValue([
      { productId: 1, productName: '価格テスト', minPrice: 1000, totalStock: 1, preorderStatus: 'ON_SALE' },
    ]);
    renderProductList();

    await waitFor(() => {
      expect(screen.getByText('¥1,000')).toBeInTheDocument();
    });
  });

  it('PRE_ORDER 商品の価格は ¥X,XXX 〜 形式で表示する', async () => {
    api.getMarketProducts.mockResolvedValue([
      {
        productId: 9, productName: '予約価格テスト', minPrice: 1500, totalStock: 0,
        preorderStatus: 'PRE_ORDER', releaseDate: '2026-08-01',
      },
    ]);
    renderProductList();

    await waitFor(() => {
      expect(screen.getByText(/¥1,500\s*〜/)).toBeInTheDocument();
    });
  });

  it('minPrice が null のときは価格行ごと非表示にする', async () => {
    api.getMarketProducts.mockResolvedValue([
      {
        productId: 99, productName: '価格未設定商品', minPrice: null, totalStock: 0,
        preorderStatus: 'PRE_ORDER', releaseDate: '2026-08-01',
      },
    ]);
    renderProductList();

    await waitFor(() => screen.getByText('価格未設定商品'));
    expect(screen.queryByText(/¥/)).not.toBeInTheDocument();
    expect(screen.queryByText(/—/)).not.toBeInTheDocument();
  });

  // フェーズ14.5 §4-2: ステータス別ラベルと補足表示
  it('ON_SALE は「通常販売」ラベルと「残りN点」を表示する', async () => {
    api.getMarketProducts.mockResolvedValue([
      { productId: 1, productName: '通常商品', minPrice: 1000, totalStock: 7, preorderStatus: 'ON_SALE' },
    ]);
    renderProductList();
    await waitFor(() => {
      expect(screen.getByText('通常販売')).toBeInTheDocument();
      expect(screen.getByText('残り7点')).toBeInTheDocument();
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
      // ON_SALE 以外は在庫数（「残りN点」）を表示しない
      expect(screen.queryByText(/残り\d+点/)).not.toBeInTheDocument();
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

  it('?q=テスト 付きでアクセスするとキーワードを含む商品だけ表示される', async () => {
    api.getMarketProducts.mockResolvedValue([
      { productId: 1, productName: 'テスト商品A', minPrice: 1000, totalStock: 1, preorderStatus: 'ON_SALE' },
      { productId: 2, productName: 'りんご', minPrice: 200, totalStock: 1, preorderStatus: 'ON_SALE' },
      { productId: 3, productName: 'テスト商品B', minPrice: 3000, totalStock: 1, preorderStatus: 'ON_SALE' },
    ]);
    renderProductList('/?q=' + encodeURIComponent('テスト'));

    await waitFor(() => {
      expect(screen.getByText('テスト商品A')).toBeInTheDocument();
      expect(screen.getByText('テスト商品B')).toBeInTheDocument();
      expect(screen.queryByText('りんご')).not.toBeInTheDocument();
    });
    expect(screen.getByText(/「テスト」の検索結果（2件）/)).toBeInTheDocument();
  });

  it('検索ヒット0件時は「該当する商品がありません」と「すべての商品を見る」リンクを表示する', async () => {
    api.getMarketProducts.mockResolvedValue([
      { productId: 1, productName: 'りんご', minPrice: 200, totalStock: 1, preorderStatus: 'ON_SALE' },
    ]);
    renderProductList('/?q=' + encodeURIComponent('みかん'));

    await waitFor(() => {
      expect(screen.getByText('該当する商品がありません')).toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: 'すべての商品を見る' })).toHaveAttribute('href', '/');
  });

  it('「価格の安い順」を選ぶと minPrice 昇順で並ぶ', async () => {
    api.getMarketProducts.mockResolvedValue([
      { productId: 1, productName: '商品B', minPrice: 3000, totalStock: 1, preorderStatus: 'ON_SALE' },
      { productId: 2, productName: '商品A', minPrice: 1000, totalStock: 1, preorderStatus: 'ON_SALE' },
      { productId: 3, productName: '商品C', minPrice: 2000, totalStock: 1, preorderStatus: 'ON_SALE' },
    ]);
    const user = userEvent.setup();
    renderProductList();
    await waitFor(() => screen.getByText('商品A'));

    await user.click(screen.getByLabelText('並び替え'));
    await user.click(await screen.findByRole('option', { name: '価格の安い順' }));

    await waitFor(() => {
      const headings = within(document.body).getAllByText(/^商品[ABC]$/);
      const order = headings.map((el) => el.textContent);
      expect(order).toEqual(['商品A', '商品C', '商品B']);
    });
  });
});
