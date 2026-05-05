-- フェーズ12: ワークフロー機能向けロール体系拡張
-- 既存 admin / user に加えて supervisor / senior_admin / eternal_advisor を追加する。

INSERT INTO roles (code, name) VALUES ('supervisor',      'スーパーバイザー');
INSERT INTO roles (code, name) VALUES ('senior_admin',    '上位管理者');
INSERT INTO roles (code, name) VALUES ('eternal_advisor', 'エターナルフォースバイザー');

-- ワークフロー画面の permissions
INSERT INTO permissions (screen_id, name) VALUES ('workflows.list',    'ワークフロー一覧');
INSERT INTO permissions (screen_id, name) VALUES ('workflows.detail',  'ワークフロー詳細');
INSERT INTO permissions (screen_id, name) VALUES ('workflows.request', 'ワークフロー申請');
INSERT INTO permissions (screen_id, name) VALUES ('workflows.approve', 'ワークフロー承認');
INSERT INTO permissions (screen_id, name) VALUES ('workflows.apply',   'ワークフロー即時反映');

-- admin / senior_admin / eternal_advisor は全権限
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code IN ('senior_admin', 'eternal_advisor');

-- supervisor: 商品・SKU・売上・ワークフロー（即時反映含む）
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code = 'supervisor'
      AND p.screen_id IN (
        'products.list', 'products.create', 'products.edit',
        'skus.list', 'sales.list',
        'workflows.list', 'workflows.detail', 'workflows.request',
        'workflows.approve', 'workflows.apply'
      );

-- 既存 admin にもワークフロー権限を付与
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code = 'admin'
      AND p.screen_id LIKE 'workflows.%'
      AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
        WHERE rp.role_id = r.id AND rp.permission_id = p.id
      );

-- 既存 user にもワークフロー閲覧・申請権限を付与（承認・反映は不可）
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code = 'user'
      AND p.screen_id IN ('workflows.list', 'workflows.detail', 'workflows.request');
