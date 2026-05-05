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
