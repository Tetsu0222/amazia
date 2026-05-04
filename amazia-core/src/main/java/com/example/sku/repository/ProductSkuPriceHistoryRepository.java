package com.example.sku.repository;

import com.example.sku.entity.ProductSkuPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductSkuPriceHistoryRepository extends JpaRepository<ProductSkuPriceHistory, Long> {

    List<ProductSkuPriceHistory> findBySkuIdOrderByStartDateAsc(Long skuId);
}
