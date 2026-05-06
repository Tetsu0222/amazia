package com.example.sku.repository;

import com.example.sku.entity.ProductSkuStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductSkuStockRepository extends JpaRepository<ProductSkuStock, Long> {

    Optional<ProductSkuStock> findBySkuId(Long skuId);

    List<ProductSkuStock> findBySkuIdIn(List<Long> skuIds);

    @Query(value =
            "SELECT COALESCE(SUM(s.quantity), 0) " +
            "FROM product_sku_stocks s " +
            "WHERE s.sku_id IN (SELECT id FROM product_skus WHERE product_id = :productId)",
            nativeQuery = true)
    long sumQuantityByProductId(@Param("productId") Long productId);
}
