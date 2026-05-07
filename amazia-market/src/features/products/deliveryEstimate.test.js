import { describe, it, expect } from 'vitest';
import { getEstimatedDeliveryDate } from './deliveryEstimate';

describe('getEstimatedDeliveryDate', () => {
  it('PRE_ORDER は releaseDate を返す', () => {
    const data = { preorderStatus: 'PRE_ORDER', product: { releaseDate: '2026-08-01' } };
    expect(getEstimatedDeliveryDate(data)).toBe('2026-08-01');
  });

  it('PRE_ORDER で releaseDate が無い場合は null', () => {
    const data = { preorderStatus: 'PRE_ORDER', product: {} };
    expect(getEstimatedDeliveryDate(data)).toBeNull();
  });

  it('PRE_ORDER_NOT_STARTED は null（お届け予定未定）', () => {
    const data = { preorderStatus: 'PRE_ORDER_NOT_STARTED', product: { preorderStartDate: '2026-09-01' } };
    expect(getEstimatedDeliveryDate(data)).toBeNull();
  });

  it('BACK_ORDER は null', () => {
    const data = { preorderStatus: 'BACK_ORDER', product: {} };
    expect(getEstimatedDeliveryDate(data)).toBeNull();
  });

  it('ON_SALE は今日 + 3 日の YYYY-MM-DD を返す', () => {
    const today = new Date('2026-05-08T00:00:00');
    const data = { preorderStatus: 'ON_SALE', product: {} };
    expect(getEstimatedDeliveryDate(data, today)).toBe('2026-05-11');
  });

  it('null データは ON_SALE 扱いで今日 + 3 日を返す（防御的）', () => {
    const today = new Date('2026-12-30T00:00:00');
    expect(getEstimatedDeliveryDate(null, today)).toBe('2027-01-02');
  });
});
