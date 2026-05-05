-- 冪等な初期データ投入（INSERT IGNORE で重複スキップ）

INSERT IGNORE INTO roles (code, name) VALUES ('admin', '管理者');
INSERT IGNORE INTO roles (code, name) VALUES ('user',  '一般');

INSERT IGNORE INTO permissions (screen_id, name) VALUES ('users.list',      '社員一覧');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('users.create',    '社員登録');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('users.edit',      '社員編集');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('products.list',   '商品マスタ一覧');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('products.create', '商品登録');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('products.edit',   '商品編集');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('skus.list',       'SKU管理');
INSERT IGNORE INTO permissions (screen_id, name) VALUES ('sales.list',      '売上管理');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'admin';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code = 'user'
      AND p.screen_id IN ('products.list', 'products.create', 'products.edit', 'skus.list', 'sales.list');

-- admin アカウント (パスワード: Admin@2024!)
INSERT IGNORE INTO users (employee_id, email, name, password_hash, role_id, active_flag, failed_attempts)
    SELECT 'EMP000', 'admin@amazia.example.com', 'システム管理者',
           '$2y$12$FCd83k8VdjiOXciMC9SoIuM.Hlsql8BgnamsGn79jOzNo105Iu36a',
           id, TRUE, 0
    FROM roles WHERE code = 'admin';
