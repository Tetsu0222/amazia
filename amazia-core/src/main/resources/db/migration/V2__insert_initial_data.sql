-- roles
INSERT INTO roles (code, name) VALUES ('admin', '管理者');
INSERT INTO roles (code, name) VALUES ('user',  '一般');

-- permissions (screen_id = Vue Router のパス相当)
INSERT INTO permissions (screen_id, name) VALUES ('users.list',   '社員一覧');
INSERT INTO permissions (screen_id, name) VALUES ('users.create', '社員登録');
INSERT INTO permissions (screen_id, name) VALUES ('users.edit',   '社員編集');
INSERT INTO permissions (screen_id, name) VALUES ('products.list',   '商品マスタ一覧');
INSERT INTO permissions (screen_id, name) VALUES ('products.create', '商品登録');
INSERT INTO permissions (screen_id, name) VALUES ('products.edit',   '商品編集');
INSERT INTO permissions (screen_id, name) VALUES ('skus.list', 'SKU管理');
INSERT INTO permissions (screen_id, name) VALUES ('sales.list', '売上管理');

-- role_permissions: admin に全権限
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'admin';

-- role_permissions: user に商品・SKU・売上権限
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code = 'user'
      AND p.screen_id IN ('products.list', 'products.create', 'products.edit', 'skus.list', 'sales.list');

-- admin ユーザー (パスワードはランダム生成済み BCrypt ハッシュ: "Admin@2024!")
INSERT INTO users (employee_id, email, name, password_hash, role_id, active_flag)
    SELECT 'EMP000', 'admin@amazia.example.com', 'システム管理者',
           '$2a$12$eImiTXuWVxfM37uY4JANjQ==invalid_placeholder_change_before_prod',
           id, TRUE
    FROM roles WHERE code = 'admin';
