-- ============================================================================
-- ローカル開発専用の商品サンプル seed（044 派生節 (3) 対応）
--
-- 本ファイルは application-local.properties の spring.sql.init.data-locations で
-- data.sql に追加して読み込まれる。本番（dev プロファイル）では読み込まれない。
--
-- ローカルで `docker compose down -v` 後の初回起動でも商品一覧画面が
-- 空配列ではなくサンプル 3 件で表示されるよう最小限のデータを投入する。
-- 各テーブルは UNIQUE 制約（sku_code 等）と INSERT IGNORE で冪等性を担保。
-- ============================================================================

INSERT IGNORE INTO products (id, name, description, price, stock, status_code,
                             publish_start, publish_end, version, created_at, updated_at) VALUES
    (1001, 'サンプル Tシャツ',     'サンプル用の白 Tシャツ。', 2980, 100, 'ON_SALE',
            '2026-01-01 00:00:00', NULL, 0, NOW(6), NOW(6)),
    (1002, 'サンプル スニーカー',  'サンプル用の黒スニーカー。', 8980, 50,  'ON_SALE',
            '2026-01-01 00:00:00', NULL, 0, NOW(6), NOW(6)),
    (1003, 'サンプル 予約ジャケット', '冬物の予約販売サンプル。', 19800, 0,  'RESERVATION',
            '2026-12-01 00:00:00', NULL, 0, NOW(6), NOW(6));

INSERT IGNORE INTO product_skus (id, product_id, sku_code, color, size, status, created_at, updated_at) VALUES
    (2001, 1001, 'TSHIRT-WHITE-M',     'WHITE', 'M',  'ACTIVE', NOW(6), NOW(6)),
    (2002, 1002, 'SNEAKER-BLACK-26',   'BLACK', '26', 'ACTIVE', NOW(6), NOW(6)),
    (2003, 1003, 'JACKET-NAVY-L',      'NAVY',  'L',  'ACTIVE', NOW(6), NOW(6));

INSERT IGNORE INTO product_sku_prices (id, sku_id, price, start_date, end_date, version, created_at, updated_at) VALUES
    (3001, 2001, 2980,  '2026-01-01', NULL, 0, NOW(6), NOW(6)),
    (3002, 2002, 8980,  '2026-01-01', NULL, 0, NOW(6), NOW(6)),
    (3003, 2003, 19800, '2026-01-01', NULL, 0, NOW(6), NOW(6));

INSERT IGNORE INTO product_sku_stocks (id, sku_id, quantity, version, created_at, updated_at) VALUES
    (4001, 2001, 100, 0, NOW(6), NOW(6)),
    (4002, 2002, 50,  0, NOW(6), NOW(6)),
    (4003, 2003, 0,   0, NOW(6), NOW(6));
