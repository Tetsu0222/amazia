-- 冪等な初期データ投入（INSERT IGNORE で重複スキップ）

INSERT IGNORE INTO roles (code, name) VALUES ('admin',           '管理者');
INSERT IGNORE INTO roles (code, name) VALUES ('user',            '一般');
INSERT IGNORE INTO roles (code, name) VALUES ('supervisor',      'スーパーバイザー');
INSERT IGNORE INTO roles (code, name) VALUES ('senior_admin',    '上位管理者');
INSERT IGNORE INTO roles (code, name) VALUES ('eternal_advisor', 'エターナルフォースバイザー');

INSERT IGNORE INTO permissions (screen_id, name) VALUES ('users.list',        '社員一覧');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('users.create',      '社員登録');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('users.edit',        '社員編集');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('products.list',     '商品マスタ一覧');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('products.create',   '商品登録');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('products.edit',     '商品編集');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('skus.list',         'SKU管理');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('sales.list',        '売上管理');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('workflows.list',    'ワークフロー一覧');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('workflows.detail',  'ワークフロー詳細');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('workflows.request', 'ワークフロー申請');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('workflows.approve', 'ワークフロー承認');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('workflows.apply',   'ワークフロー即時反映');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code IN ('admin', 'senior_admin', 'eternal_advisor');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code = 'supervisor'
      AND p.screen_id IN (
        'products.list', 'products.create', 'products.edit',
        'skus.list', 'sales.list',
        'workflows.list', 'workflows.detail', 'workflows.request',
        'workflows.approve', 'workflows.apply'
      );

INSERT IGNORE INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code = 'user'
      AND p.screen_id IN (
        'products.list', 'products.create', 'products.edit',
        'skus.list', 'sales.list',
        'workflows.list', 'workflows.detail', 'workflows.request'
      );

-- admin アカウント (パスワード: Admin@2024!)
INSERT IGNORE INTO users (employee_id, email, name, password_hash, role_id, active_flag, failed_attempts)
    SELECT 'EMP000', 'admin@amazia.example.com', 'システム管理者',
           '$2y$12$FCd83k8VdjiOXciMC9SoIuM.Hlsql8BgnamsGn79jOzNo105Iu36a',
           id, TRUE, 0
    FROM roles WHERE code = 'admin';

-- 各ロール seed (パスワード: Admin@2024! 共通) - 開発用
INSERT IGNORE INTO users (employee_id, email, name, password_hash, role_id, active_flag, failed_attempts)
    SELECT 'EMP100', 'supervisor@amazia.example.com', 'スーパーバイザー太郎',
           '$2y$12$FCd83k8VdjiOXciMC9SoIuM.Hlsql8BgnamsGn79jOzNo105Iu36a',
           id, TRUE, 0
    FROM roles WHERE code = 'supervisor';

INSERT IGNORE INTO users (employee_id, email, name, password_hash, role_id, active_flag, failed_attempts)
    SELECT 'EMP200', 'senior@amazia.example.com', '上位管理者花子',
           '$2y$12$FCd83k8VdjiOXciMC9SoIuM.Hlsql8BgnamsGn79jOzNo105Iu36a',
           id, TRUE, 0
    FROM roles WHERE code = 'senior_admin';

INSERT IGNORE INTO users (employee_id, email, name, password_hash, role_id, active_flag, failed_attempts)
    SELECT 'EMP900', 'eternal@amazia.example.com', 'エターナル賢者',
           '$2y$12$FCd83k8VdjiOXciMC9SoIuM.Hlsql8BgnamsGn79jOzNo105Iu36a',
           id, TRUE, 0
    FROM roles WHERE code = 'eternal_advisor';

INSERT IGNORE INTO users (employee_id, email, name, password_hash, role_id, active_flag, failed_attempts)
    SELECT 'EMP500', 'user@amazia.example.com', '一般社員',
           '$2y$12$FCd83k8VdjiOXciMC9SoIuM.Hlsql8BgnamsGn79jOzNo105Iu36a',
           id, TRUE, 0
    FROM roles WHERE code = 'user';

-- ============================================================================
-- 商品マスタ seed（044 派生節 (3) のボリューム再作成からの開発再開のため最小限投入）
--   - product_statuses マスタ（WAITING / RESERVATION / ON_SALE）
--   - 商品 3 件（販売中 2 / 予約受付中 1）
--   - 各商品に SKU 1 件、価格 1 件、在庫 1 件
--   各テーブルは UNIQUE (sku_code 等) で冪等性を担保。再投入されても重複しない。
-- ============================================================================

INSERT IGNORE INTO product_statuses (code, name, sort_order) VALUES
    ('WAITING',     '入荷待',    1),
    ('RESERVATION', '予約受付中', 2),
    ('ON_SALE',     '販売中',    3);

-- 商品本体（id を固定で参照する設計のため、あえて id 指定で投入）
INSERT IGNORE INTO products (id, name, description, price, stock, status_code,
                             publish_start, publish_end, version, created_at, updated_at) VALUES
    (1001, 'サンプル Tシャツ',     'サンプル用の白 Tシャツ。', 2980, 100, 'ON_SALE',
            '2026-01-01 00:00:00', NULL, 0, NOW(6), NOW(6)),
    (1002, 'サンプル スニーカー',  'サンプル用の黒スニーカー。', 8980, 50,  'ON_SALE',
            '2026-01-01 00:00:00', NULL, 0, NOW(6), NOW(6)),
    (1003, 'サンプル 予約ジャケット', '冬物の予約販売サンプル。', 19800, 0,  'RESERVATION',
            '2026-12-01 00:00:00', NULL, 0, NOW(6), NOW(6));

-- SKU（unique key: product_id × color × size でも sku_code でも衝突しないよう固定値で投入）
INSERT IGNORE INTO product_skus (id, product_id, sku_code, color, size, status, created_at, updated_at) VALUES
    (2001, 1001, 'TSHIRT-WHITE-M', 'WHITE', 'M', 'ACTIVE', NOW(6), NOW(6)),
    (2002, 1002, 'SNEAKER-BLACK-26', 'BLACK', '26', 'ACTIVE', NOW(6), NOW(6)),
    (2003, 1003, 'JACKET-NAVY-L', 'NAVY', 'L', 'ACTIVE', NOW(6), NOW(6));

-- 現行価格
INSERT IGNORE INTO product_sku_prices (id, sku_id, price, start_date, end_date, version, created_at, updated_at) VALUES
    (3001, 2001, 2980,  '2026-01-01', NULL, 0, NOW(6), NOW(6)),
    (3002, 2002, 8980,  '2026-01-01', NULL, 0, NOW(6), NOW(6)),
    (3003, 2003, 19800, '2026-01-01', NULL, 0, NOW(6), NOW(6));

-- 在庫（sku_id UNIQUE）
INSERT IGNORE INTO product_sku_stocks (id, sku_id, quantity, version, created_at, updated_at) VALUES
    (4001, 2001, 100, 0, NOW(6), NOW(6)),
    (4002, 2002, 50,  0, NOW(6), NOW(6)),
    (4003, 2003, 0,   0, NOW(6), NOW(6));

-- マスタ系（warehouses / shipping_methods / shipping_statuses / payment_methods）の
-- 初期 INSERT は schema.sql 内で一緒に行われるため、本ファイルでは商品 seed のみ扱う。
