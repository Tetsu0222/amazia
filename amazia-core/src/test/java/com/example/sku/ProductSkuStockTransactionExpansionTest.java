package com.example.sku;

import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ14 Step A: ProductSkuStockTransaction の拡張カラム検証。
 *
 * V11 で追加したカラム（reference_type / reference_id / created_by_user_id / comment）
 * と、既存に追加した type 値（sale / return / cancel）が
 * JPA 経由で読み書きできることを確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ProductSkuStockTransactionExpansionTest {

    @Autowired
    private ProductSkuStockTransactionRepository repository;

    @Test
    void sale_type_と_reference_情報を含めて保存と取得ができる() {
        ProductSkuStockTransaction tx = new ProductSkuStockTransaction();
        tx.setSkuId(100L);
        tx.setType("sale");          // r4 で許容追加
        tx.setQuantity(-2);          // 販売は負数
        tx.setReferenceType("sales");
        tx.setReferenceId(999L);
        tx.setCreatedByUserId(1L);   // 通常購入の場合は注文者の user_id

        ProductSkuStockTransaction saved = repository.save(tx);
        assertNotNull(saved.getId());

        ProductSkuStockTransaction loaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals("sale", loaded.getType());
        assertEquals(-2, loaded.getQuantity());
        assertEquals("sales", loaded.getReferenceType());
        assertEquals(999L, loaded.getReferenceId());
        assertEquals(1L, loaded.getCreatedByUserId());
    }

    @Test
    void return_type_でコメント付きで保存できる() {
        ProductSkuStockTransaction tx = new ProductSkuStockTransaction();
        tx.setSkuId(100L);
        tx.setType("return");
        tx.setQuantity(1);
        tx.setReferenceType("sales_return");
        tx.setReferenceId(50L);
        tx.setCreatedByUserId(2L);   // 返品承認した管理者の users.id
        tx.setComment("色違いによる返品");

        ProductSkuStockTransaction saved = repository.save(tx);
        ProductSkuStockTransaction loaded = repository.findById(saved.getId()).orElseThrow();

        assertEquals("return", loaded.getType());
        assertEquals(1, loaded.getQuantity());
        assertEquals("色違いによる返品", loaded.getComment());
    }

    @Test
    void 既存_receive_type_は引き続き使用できる() {
        // フェーズ10 既存の入荷ログが r4 拡張後も壊れないことを確認
        ProductSkuStockTransaction tx = new ProductSkuStockTransaction();
        tx.setSkuId(100L);
        tx.setType("receive");
        tx.setQuantity(10);
        // reference_type / reference_id / created_by_user_id / comment は NULL 許容

        ProductSkuStockTransaction saved = repository.save(tx);
        ProductSkuStockTransaction loaded = repository.findById(saved.getId()).orElseThrow();

        assertEquals("receive", loaded.getType());
        assertEquals(10, loaded.getQuantity());
        assertNull(loaded.getReferenceType());
        assertNull(loaded.getReferenceId());
        assertNull(loaded.getCreatedByUserId());
    }
}
