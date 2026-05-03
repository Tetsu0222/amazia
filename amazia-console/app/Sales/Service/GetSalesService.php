<?php

namespace App\Sales\Service;

class GetSalesService
{
    public function getSales(): array
    {
        return [
            ['id' => 1, 'product' => '商品A', 'amount' => 5000, 'quantity' => 3],
            ['id' => 2, 'product' => '商品B', 'amount' => 3000, 'quantity' => 1],
            ['id' => 3, 'product' => '商品C', 'amount' => 8000, 'quantity' => 2],
        ];
    }
}
