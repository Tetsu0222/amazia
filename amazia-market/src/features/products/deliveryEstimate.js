// フェーズ16.5 §Step 6 スタブ実装。phaseX-5（都道府県別リードタイム）導入時に実機能化。
// PRE_ORDER は releaseDate を返し、ON_SALE は今日 + 3 日を返す。

export function getEstimatedDeliveryDate(productData, today = new Date()) {
  const status = productData?.preorderStatus;
  const releaseDate = productData?.product?.releaseDate;

  if (status === 'PRE_ORDER') {
    return releaseDate ?? null;
  }
  if (status === 'PRE_ORDER_NOT_STARTED' || status === 'BACK_ORDER') {
    return null;
  }
  // ON_SALE のスタブ: 今日 + 3 日
  const d = new Date(today);
  d.setDate(d.getDate() + 3);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}
