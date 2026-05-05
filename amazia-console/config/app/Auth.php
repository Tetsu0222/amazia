<?php

return [
    'jwt_secret' => env('JWT_SECRET'),

    'role_permissions' => [
        'admin' => ['*'],
        'user'  => ['products.*', 'skus.*', 'sales.*'],
    ],
];
