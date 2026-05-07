/**
 * 操作履歴画面の表示ラベル定義（config 駆動）。
 *
 * 設計書: docs/design/phase11_20/phase16_ui_ux_improvement.md §Step 4
 *
 * Core 側の各 Service が記録する英字キー（`private static final String ACTION` 等）と
 * 画面表示用の和名の対応関係を集約する。Core で新規 action / screen が追加された場合は
 * 本ファイルにエントリを 1 行追加するだけで画面が和名表示に追従する。
 *
 * フォールバック方針：マップに存在しないキーは原文をそのまま表示する（labelOr）。
 */

export const ACTION_LABELS = {
  register_inbound: '入荷登録',
  update_shipping_status: '配送ステータス更新',
  update_shipping_address: '配送先住所更新',
  update_scheduled_date: '配送予定日更新',
  register_tracking_code: '追跡番号登録',
  approve_sales_return: '返品承認',
  reject_sales_return: '返品却下',
  refund_sales_return: '返金処理',
};

export const TARGET_TYPE_LABELS = {
  inbounds: '入荷',
  deliveries: '配送',
  sales_return: '返品',
};

export const SCREEN_NAME_LABELS = {
  'console.inbound.register': '入荷登録画面',
  'console.delivery.update_status': '配送ステータス更新',
  'console.delivery.update_address': '配送先住所更新',
  'console.delivery.update_scheduled_date': '配送予定日更新',
  'console.delivery.register_tracking': '追跡番号登録',
  'console.sales_return.approve': '返品承認画面',
  'core.batch.inbound_recalc': '入荷再計算バッチ',
};

export const API_NAME_LABELS = {
  'POST /api/inbounds': '入荷登録 API',
  'PATCH /api/deliveries/:id/status': '配送ステータス更新 API',
  'PATCH /api/deliveries/:id/address': '配送先住所更新 API',
  'PATCH /api/deliveries/:id/scheduled-date': '配送予定日更新 API',
  'PATCH /api/deliveries/:id/tracking-code': '追跡番号登録 API',
  'POST /api/sales-returns/:id/approve': '返品承認 API',
  'POST /api/sales-returns/:id/reject': '返品却下 API',
  'POST /api/sales-returns/:id/refund': '返金処理 API',
};

export function labelOr(map, key) {
  if (key === null || key === undefined || key === '') return '';
  return map[key] ?? key;
}
