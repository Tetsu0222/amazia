<?php

namespace Tests\Feature;

use Tests\TestCase;

class SalesControllerTest extends TestCase
{
    public function test_売上一覧が取得できること(): void
    {
        $response = $this->getJson('/api/sales');

        $response->assertStatus(200)
                 ->assertJsonCount(3)
                 ->assertJsonFragment(['product' => '商品A'])
                 ->assertJsonFragment(['product' => '商品B'])
                 ->assertJsonFragment(['product' => '商品C']);
    }
}
