package com.example.sku.repository;

import com.example.sku.entity.ProductSkuPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductSkuPriceRepository extends JpaRepository<ProductSkuPrice, Long> {

    Optional<ProductSkuPrice> findBySkuId(Long skuId);

    List<ProductSkuPrice> findBySkuIdIn(List<Long> skuIds);
}
