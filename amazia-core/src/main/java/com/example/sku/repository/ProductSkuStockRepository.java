package com.example.sku.repository;

import com.example.sku.entity.ProductSkuStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductSkuStockRepository extends JpaRepository<ProductSkuStock, Long> {

    Optional<ProductSkuStock> findBySkuId(Long skuId);
}
