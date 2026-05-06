// フェーズ14.5 §4-2: 予約ステータス別の Market 表示メタデータ
// （NOT_PUBLIC は Core 側で一覧から除外されるため UI 表示対象外）

export const PREORDER_STATUS = {
  NOT_PUBLIC:            'NOT_PUBLIC',
  PRE_ORDER_NOT_STARTED: 'PRE_ORDER_NOT_STARTED',
  PRE_ORDER:             'PRE_ORDER',
  ON_SALE:               'ON_SALE',
  BACK_ORDER:            'BACK_ORDER',
  SOLD_OUT:              'SOLD_OUT',
};

// status -> { label, chipColor, buttonLabel, buttonAction }
//   buttonAction: 'buy' | 'preorder' | 'disabled' | 'hidden'
export const PREORDER_STATUS_META = {
  PRE_ORDER_NOT_STARTED: {
    label:        '予約開始前',
    chipColor:    'default',
    buttonLabel:  '予約する',
    buttonAction: 'disabled',
  },
  PRE_ORDER: {
    label:        '予約受付中',
    chipColor:    'info',
    buttonLabel:  '予約する',
    buttonAction: 'preorder',
  },
  ON_SALE: {
    label:        '通常販売',
    chipColor:    'success',
    buttonLabel:  '購入する',
    buttonAction: 'buy',
  },
  BACK_ORDER: {
    label:        '再入荷予約受付中',
    chipColor:    'warning',
    buttonLabel:  '予約する',
    buttonAction: 'preorder',
  },
  SOLD_OUT: {
    label:        '完売',
    chipColor:    'error',
    buttonLabel:  null,
    buttonAction: 'hidden',
  },
};

export function getPreorderStatusMeta(status) {
  return PREORDER_STATUS_META[status] ?? null;
}
