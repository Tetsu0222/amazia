<?php

return [
    /*
     * 申請対象 target_type ホワイトリスト
     */
    'target_types' => ['product', 'price', 'stock'],

    /*
     * 申請フォームの validation ルール（FormRequest から参照）
     */
    'create_validation' => [
        'targetType'           => 'required|string|in:product,price,stock',
        'targetId'             => 'required|integer|min:1',
        'fields'               => 'required|array|min:1',
        'fields.*.field'       => 'required|string',
        'fields.*.before'      => 'nullable',
        'fields.*.after'       => 'nullable',
        'meta'                 => 'nullable|array',
        'meta.reason'          => 'nullable|string|max:1000',
        'destinationUserIds'   => 'nullable|array',
    ],
];
