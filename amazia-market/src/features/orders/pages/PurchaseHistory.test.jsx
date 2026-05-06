import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import PurchaseHistory from './PurchaseHistory';
import * as api from '../../checkout/api/checkout';
import * as returnApi from '../api/salesReturn';

vi.mock('../../checkout/api/checkout');
vi.mock('../api/salesReturn');

function renderPage() {
  return render(
    <MemoryRouter>
      <PurchaseHistory />
    </MemoryRouter>
  );
}

const baseItem = {
  salesId: 1,
  salesDate: '2026-05-06',
  shippingDate: null,
  skuId: 100,
  productName: 'テストTシャツ',
  color: '赤',
  size: 'M',
  quantity: 2,
  amount: 6000,
  shippingStatusCode: 'PENDING',
  shippingMethodId: 1,
  paymentMethodId: 1,
  preorder: false,
};

describe('PurchaseHistory', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('購入履歴 1 件を表示する', async () => {
    api.getMyPurchaseHistory.mockResolvedValue([baseItem]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('テストTシャツ')).toBeInTheDocument();
      expect(screen.getByText('赤 / M')).toBeInTheDocument();
      expect(screen.getByText('¥6,000')).toBeInTheDocument();
      expect(screen.getByText('配送準備中')).toBeInTheDocument();
      expect(screen.getByText('通常')).toBeInTheDocument();
      expect(screen.getByText('宅配')).toBeInTheDocument();
      expect(screen.getByText('クレジットカード')).toBeInTheDocument();
    });
  });

  it('購入履歴がない場合は空メッセージを表示する', async () => {
    api.getMyPurchaseHistory.mockResolvedValue([]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('購入履歴はまだありません。')).toBeInTheDocument();
    });
  });

  it('予約購入は「予約」と表示される', async () => {
    api.getMyPurchaseHistory.mockResolvedValue([{ ...baseItem, salesId: 2, preorder: true, productName: '予約商品', color: '黒', size: 'L', quantity: 1, amount: 5000 }]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('予約')).toBeInTheDocument();
    });
  });

  it('API エラー時はエラーメッセージを表示する', async () => {
    api.getMyPurchaseHistory.mockRejectedValue(new Error('network'));
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('購入履歴の取得に失敗しました。')).toBeInTheDocument();
    });
  });

  // ---- B-5-7: 返品申請 ----------------------------------------------

  it('DELIVERED の sales には返品申請ボタンを表示する', async () => {
    api.getMyPurchaseHistory.mockResolvedValue([{ ...baseItem, shippingStatusCode: 'DELIVERED' }]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '返品申請' })).toBeInTheDocument();
    });
  });

  it('DELIVERED 以外には返品申請ボタンを表示しない', async () => {
    api.getMyPurchaseHistory.mockResolvedValue([
      { ...baseItem, salesId: 1, shippingStatusCode: 'PENDING' },
      { ...baseItem, salesId: 2, shippingStatusCode: 'SHIPPED' },
      { ...baseItem, salesId: 3, shippingStatusCode: 'RETURN_REQUESTED' },
      { ...baseItem, salesId: 4, shippingStatusCode: 'RETURNED' },
    ]);
    renderPage();

    await waitFor(() => {
      expect(screen.getAllByText('テストTシャツ')).toHaveLength(4);
    });
    expect(screen.queryByRole('button', { name: '返品申請' })).not.toBeInTheDocument();
  });

  it('返品申請ボタンを押すとモーダルが開き、申請するとAPIが呼ばれる', async () => {
    api.getMyPurchaseHistory.mockResolvedValue([{ ...baseItem, shippingStatusCode: 'DELIVERED' }]);
    returnApi.requestSalesReturn.mockResolvedValue({ id: 99, status: 'REQUESTED' });
    renderPage();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '返品申請' })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: '返品申請' }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: '返品申請' })).toBeInTheDocument();
    });

    const reasonField = screen.getByLabelText('返品理由（任意）');
    fireEvent.change(reasonField, { target: { value: 'サイズが合わなかった' } });

    fireEvent.click(screen.getByRole('button', { name: '申請する' }));

    await waitFor(() => {
      expect(returnApi.requestSalesReturn).toHaveBeenCalledWith({
        salesId: 1,
        quantity: 1,
        reason: 'サイズが合わなかった',
      });
    });
  });

  it('返品申請がCONFLICT(409)を返すとエラーメッセージが出る', async () => {
    api.getMyPurchaseHistory.mockResolvedValue([{ ...baseItem, shippingStatusCode: 'DELIVERED' }]);
    const err = new Error('conflict');
    err.response = { status: 409 };
    returnApi.requestSalesReturn.mockRejectedValue(err);
    renderPage();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '返品申請' })).toBeInTheDocument();
    });
    fireEvent.click(screen.getByRole('button', { name: '返品申請' }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: '返品申請' })).toBeInTheDocument();
    });
    fireEvent.click(screen.getByRole('button', { name: '申請する' }));

    await waitFor(() => {
      expect(screen.getByText('既に返品申請が進行中です。')).toBeInTheDocument();
    });
  });
});
