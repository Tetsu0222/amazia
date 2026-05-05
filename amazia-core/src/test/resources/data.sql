INSERT INTO product_statuses (code, name, sort_order) VALUES ('WAITING', '入荷待', 1);
INSERT INTO product_statuses (code, name, sort_order) VALUES ('RESERVATION', '予約受付中', 2);
INSERT INTO product_statuses (code, name, sort_order) VALUES ('ON_SALE', '販売中', 3);

-- auth initial data for tests
INSERT INTO roles (code, name) VALUES ('admin', '管理者');
INSERT INTO roles (code, name) VALUES ('user',  '一般');

INSERT INTO permissions (screen_id, name) VALUES ('users.list',      '社員一覧');
INSERT INTO permissions (screen_id, name) VALUES ('users.create',    '社員登録');
INSERT INTO permissions (screen_id, name) VALUES ('users.edit',      '社員編集');
INSERT INTO permissions (screen_id, name) VALUES ('products.list',   '商品マスタ一覧');
INSERT INTO permissions (screen_id, name) VALUES ('products.create', '商品登録');
INSERT INTO permissions (screen_id, name) VALUES ('products.edit',   '商品編集');
INSERT INTO permissions (screen_id, name) VALUES ('skus.list',       'SKU管理');
INSERT INTO permissions (screen_id, name) VALUES ('sales.list',      '売上管理');

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'admin';

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code = 'user'
      AND p.screen_id IN ('products.list', 'products.create', 'products.edit', 'skus.list', 'sales.list');
