export function searchProducts(products, keyword) {
  if (!keyword || !keyword.trim()) return products;
  const k = keyword.trim().toLowerCase();
  return products.filter((p) => {
    const name = (p.productName ?? '').toLowerCase();
    const desc = (p.description ?? '').toLowerCase();
    return name.includes(k) || desc.includes(k);
  });
}
