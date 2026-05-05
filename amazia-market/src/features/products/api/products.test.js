import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';

vi.mock('axios');

const mockClient = {
  get: vi.fn(),
  create: vi.fn(),
};
axios.create.mockReturnValue(mockClient);

// モジュールを動的インポートしてモックが先に適用されるようにする
const { getProducts, getProduct, getMarketProducts, getMarketProduct } =
  await import('./products.js');

describe('products API', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('getProducts は /products を呼ぶ', async () => {
    mockClient.get.mockResolvedValue({ data: [{ productId: 1 }] });
    const result = await getProducts();
    expect(mockClient.get).toHaveBeenCalledWith('/products');
    expect(result).toEqual([{ productId: 1 }]);
  });

  it('getProduct は /products/:id を呼ぶ', async () => {
    mockClient.get.mockResolvedValue({ data: { productId: 42 } });
    const result = await getProduct(42);
    expect(mockClient.get).toHaveBeenCalledWith('/products/42');
    expect(result).toEqual({ productId: 42 });
  });

  it('getMarketProducts は /products/market を呼ぶ', async () => {
    mockClient.get.mockResolvedValue({ data: [] });
    await getMarketProducts();
    expect(mockClient.get).toHaveBeenCalledWith('/products/market');
  });

  it('getMarketProduct は /products/:id/market を呼ぶ', async () => {
    mockClient.get.mockResolvedValue({ data: { product: {}, skus: [] } });
    await getMarketProduct(5);
    expect(mockClient.get).toHaveBeenCalledWith('/products/5/market');
  });
});
