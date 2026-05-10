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

-- フェーズX-5 マスタ（schema.sql と整合 / 都道府県別リードタイム）
-- 47都道府県 × 3配送方法 = 141行
-- 標準値: home_delivery=3 / konbini_pickup=4 / dropoff=2
-- 離島加算 +2: 北海道 / 長崎県 / 鹿児島県 / 沖縄県（厳格4県）
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '北海道',   5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '北海道',   6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '北海道',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '青森県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '青森県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '青森県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '岩手県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '岩手県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '岩手県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '宮城県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '宮城県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '宮城県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '秋田県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '秋田県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '秋田県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '山形県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '山形県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '山形県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '福島県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '福島県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '福島県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '茨城県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '茨城県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '茨城県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '栃木県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '栃木県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '栃木県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '群馬県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '群馬県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '群馬県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '埼玉県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '埼玉県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '埼玉県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '千葉県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '千葉県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '千葉県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '東京都',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '東京都',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '東京都',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '神奈川県', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '神奈川県', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '神奈川県', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '新潟県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '新潟県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '新潟県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '富山県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '富山県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '富山県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '石川県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '石川県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '石川県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '福井県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '福井県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '福井県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '山梨県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '山梨県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '山梨県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '長野県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '長野県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '長野県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '岐阜県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '岐阜県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '岐阜県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '静岡県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '静岡県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '静岡県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '愛知県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '愛知県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '愛知県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '三重県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '三重県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '三重県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '滋賀県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '滋賀県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '滋賀県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '京都府',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '京都府',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '京都府',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '大阪府',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '大阪府',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '大阪府',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '兵庫県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '兵庫県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '兵庫県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '奈良県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '奈良県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '奈良県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '和歌山県', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '和歌山県', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '和歌山県', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '鳥取県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '鳥取県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '鳥取県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '島根県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '島根県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '島根県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '岡山県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '岡山県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '岡山県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '広島県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '広島県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '広島県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '山口県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '山口県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '山口県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '徳島県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '徳島県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '徳島県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '香川県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '香川県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '香川県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '愛媛県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '愛媛県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '愛媛県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '高知県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '高知県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '高知県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '福岡県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '福岡県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '福岡県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '佐賀県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '佐賀県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '佐賀県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '長崎県',   5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '長崎県',   6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '長崎県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '熊本県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '熊本県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '熊本県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '大分県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '大分県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '大分県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '宮崎県',   3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '宮崎県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '宮崎県',   2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '鹿児島県', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '鹿児島県', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '鹿児島県', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (1, '沖縄県',   5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (2, '沖縄県',   6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shipping_lead_times (shipping_method_id, prefecture, lead_time_days, created_at, updated_at) VALUES (3, '沖縄県',   4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
