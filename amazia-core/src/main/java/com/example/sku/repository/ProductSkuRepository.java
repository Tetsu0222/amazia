package com.example.sku.repository;

import com.example.sku.entity.ProductSku;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {

    List<ProductSku> findByProductId(Long productId);

    List<ProductSku> findByProductIdOrderByIdAsc(Long productId);

    boolean existsByProductIdAndColorAndSize(Long productId, String color, String size);

    Optional<ProductSku> findBySkuCode(String skuCode);

    List<ProductSku> findByProductIdIn(List<Long> productIds);
}
