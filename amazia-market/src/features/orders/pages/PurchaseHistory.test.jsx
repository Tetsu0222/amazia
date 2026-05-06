import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import PurchaseHistory from './PurchaseHistory';
import * as api from '../../checkout/api/checkout';

vi.mock('../../checkout/api/checkout');

function renderPage() {
  return render(
    <MemoryRouter>
      <PurchaseHistory />
    </MemoryRouter>
  );
}

describe('PurchaseHistory', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('購入履歴 1 件を表示する', async () => {
    api.getMyPurchaseHistory.mockResolvedValue([
      {
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
      },
    ]);
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
    api.getMyPurchaseHistory.mockResolvedValue([
      {
        salesId: 2,
        salesDate: '2026-05-06',
        shippingDate: null,
        skuId: 200,
        productName: '予約商品',
        color: '黒',
        size: 'L',
        quantity: 1,
        amount: 5000,
        shippingStatusCode: 'PENDING',
        shippingMethodId: 1,
        paymentMethodId: 1,
        preorder: true,
      },
    ]);
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
});
