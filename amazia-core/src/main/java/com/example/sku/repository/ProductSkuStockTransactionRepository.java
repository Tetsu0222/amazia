package com.example.sku.repository;

import com.example.sku.entity.ProductSkuStockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductSkuStockTransactionRepository extends JpaRepository<ProductSkuStockTransaction, Long> {

    List<ProductSkuStockTransaction> findBySkuIdOrderByCreatedAtDesc(Long skuId);
}
