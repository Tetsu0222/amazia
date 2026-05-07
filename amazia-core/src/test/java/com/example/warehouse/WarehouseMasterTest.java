package com.example.warehouse;

import com.example.shared.config.TestAwsConfig;
import com.example.warehouse.entity.Warehouse;
import com.example.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ15 Step A: warehouses マスタ初期投入の検証（RRR-3）。
 * 並行運用ダミー倉庫として id=1 'default' の 1 件が存在することを確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class WarehouseMasterTest {

    @Autowired
    private WarehouseRepository repository;

    @Value("${amazia.delivery.default-warehouse-id}")
    private long defaultWarehouseId;

    @Test
    void warehouses_マスタはダミー1行で初期化されている() {
        assertEquals(1L, repository.count());
        Warehouse w = repository.findById(defaultWarehouseId).orElseThrow();
        assertEquals("default", w.getName());
    }
}
