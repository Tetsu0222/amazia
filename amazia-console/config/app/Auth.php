<?php

return [
    'jwt_secret' => env('JWT_SECRET'),

    /*
     * ロール定義（フェーズ12 で 5 ロールに拡張）
     */
    'roles' => [
        'user'            => '一般',
        'supervisor'      => 'スーパーバイザー',
        'admin'           => '管理者',
        'senior_admin'    => '上位管理者',
        'eternal_advisor' => 'エターナルフォースバイザー',
    ],

    /*
     * 承認権限を持つロール（一般ユーザーのみが申請者で、それ以外は承認・即時反映可）
     */
    'approver_roles' => ['supervisor', 'admin', 'senior_admin', 'eternal_advisor'],

    /*
     * 全ステップ代理承認可能な特権ロール
     */
    'eternal_roles' => ['eternal_advisor'],

    /*
     * 画面アクセス権限
     */
    'role_permissions' => [
        'admin'           => ['*'],
        'senior_admin'    => ['*'],
        'eternal_advisor' => ['*'],
        'supervisor'      => [
            'products.*', 'skus.*', 'sales.*',
            'workflows.list', 'workflows.detail', 'workflows.request',
            'workflows.approve', 'workflows.apply',
        ],
        'user'            => [
            'products.*', 'skus.*', 'sales.*',
            'workflows.list', 'workflows.detail', 'workflows.request',
        ],
    ],
];
