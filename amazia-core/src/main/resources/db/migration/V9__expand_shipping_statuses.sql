-- フェーズ14 Step A: shipping_statuses マスタ拡張
--
-- 設計書: docs/design/phase11_20/phase14_shipping.md（r4）
--
-- r4 で追加するステータス（マスタ存在 ≠ 入力許容／Q14-4）：
--   - CANCELED         発送前キャンセル        将来 phase21
--   - DELIVERY_FAILED  配達失敗・持ち戻り      将来 phase21
--   - RESCHEDULED      再配達手配中            将来 phase21
--
-- 本フェーズ（phase14 / phase15）の Service 層では未対応ステータスへの遷移リクエストはバリデーションで拒否する。
-- マスタに存在することと、Service 層が遷移を許可することは別概念。

INSERT INTO shipping_statuses (id, code, name, description) VALUES
    (6, 'CANCELED',         '発送前キャンセル',  '注文取消による配送中止（将来 phase21 で機能対応）'),
    (7, 'DELIVERY_FAILED',  '配達失敗',          '配達失敗・持ち戻り（将来 phase21 で機能対応）'),
    (8, 'RESCHEDULED',      '再配達手配中',      '再配達日変更・複数回配達（将来 phase21 で機能対応）');
