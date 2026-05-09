INSERT INTO product_statuses (code, name, sort_order) VALUES ('WAITING', '入荷待', 1);
INSERT INTO product_statuses (code, name, sort_order) VALUES ('RESERVATION', '予約受付中', 2);
INSERT INTO product_statuses (code, name, sort_order) VALUES ('ON_SALE', '販売中', 3);

-- auth initial data for tests
INSERT INTO roles (code, name) VALUES ('admin',           '管理者');
INSERT INTO roles (code, name) VALUES ('user',            '一般');
INSERT INTO roles (code, name) VALUES ('supervisor',      'スーパーバイザー');
INSERT INTO roles (code, name) VALUES ('senior_admin',    '上位管理者');
INSERT INTO roles (code, name) VALUES ('eternal_advisor', 'エターナルフォースバイザー');

INSERT INTO permissions (screen_id, name) VALUES ('users.list',        '社員一覧');
INSERT INTO permissions (screen_id, name) VALUES ('users.create',      '社員登録');
INSERT INTO permissions (screen_id, name) VALUES ('users.edit',        '社員編集');
INSERT INTO permissions (screen_id, name) VALUES ('products.list',     '商品マスタ一覧');
INSERT INTO permissions (screen_id, name) VALUES ('products.create',   '商品登録');
INSERT INTO permissions (screen_id, name) VALUES ('products.edit',     '商品編集');
INSERT INTO permissions (screen_id, name) VALUES ('skus.list',         'SKU管理');
INSERT INTO permissions (screen_id, name) VALUES ('sales.list',        '売上管理');
INSERT INTO permissions (screen_id, name) VALUES ('workflows.list',    'ワークフロー一覧');
INSERT INTO permissions (screen_id, name) VALUES ('workflows.detail',  'ワークフロー詳細');
INSERT INTO permissions (screen_id, name) VALUES ('workflows.request', 'ワークフロー申請');
INSERT INTO permissions (screen_id, name) VALUES ('workflows.approve', 'ワークフロー承認');
INSERT INTO permissions (screen_id, name) VALUES ('workflows.apply',   'ワークフロー即時反映');

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code IN ('admin', 'senior_admin', 'eternal_advisor');

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code = 'supervisor'
      AND p.screen_id IN (
        'products.list', 'products.create', 'products.edit',
        'skus.list', 'sales.list',
        'workflows.list', 'workflows.detail', 'workflows.request',
        'workflows.approve', 'workflows.apply'
      );

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code = 'user'
      AND p.screen_id IN (
        'products.list', 'products.create', 'products.edit',
        'skus.list', 'sales.list',
        'workflows.list', 'workflows.detail', 'workflows.request'
      );

-- フェーズ14 マスタ（V6 / V9 と整合）
-- created_at は JPA Entity の @PrePersist がここでは効かない（JDBC 直接 INSERT のため）。
-- H2 は CURRENT_TIMESTAMP がカラム生成時にデフォルト値として設定されないため、明示する。
INSERT INTO payment_methods (id, name, description, created_at) VALUES (1, 'credit_card',      'クレジットカード', CURRENT_TIMESTAMP);
INSERT INTO payment_methods (id, name, description, created_at) VALUES (2, 'd_payment',        'd払い',           CURRENT_TIMESTAMP);
INSERT INTO payment_methods (id, name, description, created_at) VALUES (3, 'cash_on_delivery', '代引き',          CURRENT_TIMESTAMP);

INSERT INTO shipping_statuses (id, code, name, description, created_at) VALUES (1, 'PENDING',          '配送準備中', '注文確定後・出荷前',     CURRENT_TIMESTAMP);
INSERT INTO shipping_statuses (id, code, name, description, created_at) VALUES (2, 'SHIPPED',          '配送済',     '発送完了',               CURRENT_TIMESTAMP);
INSERT INTO shipping_statuses (id, code, name, description, created_at) VALUES (3, 'DELIVERED',        '配送完了',   '配達完了',               CURRENT_TIMESTAMP);
INSERT INTO shipping_statuses (id, code, name, description, created_at) VALUES (4, 'RETURN_REQUESTED', '返品申請中', '返品申請を受付中',       CURRENT_TIMESTAMP);
INSERT INTO shipping_statuses (id, code, name, description, created_at) VALUES (5, 'RETURNED',         '返品完了',   '返品処理完了',           CURRENT_TIMESTAMP);
INSERT INTO shipping_statuses (id, code, name, description, created_at) VALUES (6, 'CANCELED',         '発送前キャンセル', '将来 phase21',     CURRENT_TIMESTAMP);
INSERT INTO shipping_statuses (id, code, name, description, created_at) VALUES (7, 'DELIVERY_FAILED',  '配達失敗',         '将来 phase21',     CURRENT_TIMESTAMP);
INSERT INTO shipping_statuses (id, code, name, description, created_at) VALUES (8, 'RESCHEDULED',      '再配達手配中',     '将来 phase21',     CURRENT_TIMESTAMP);

-- フェーズ15 マスタ（schema.sql と整合 / P5-1 / RRR-3）
INSERT INTO shipping_methods (id, name, description) VALUES (1, 'home_delivery',  '宅配');
INSERT INTO shipping_methods (id, name, description) VALUES (2, 'konbini_pickup', 'コンビニ受取');
INSERT INTO shipping_methods (id, name, description) VALUES (3, 'dropoff',        '置き配');

INSERT INTO warehouses (id, name, description) VALUES (1, 'default', '全社単一倉庫');

-- フェーズ19 マスタ（schema.sql と整合 / R19-1）
-- 本番（schema.sql）は INSERT IGNORE で投入されるが、H2 は ddl-auto=create-drop でスキーマ
-- を作り直すたびに空になる。テスト用マスタはここで明示的に seed する。
INSERT INTO notice_categories (id, code, label, display_order) VALUES (1, 'important', '重要', 1);
INSERT INTO notice_categories (id, code, label, display_order) VALUES (2, 'normal',    '普通', 2);
