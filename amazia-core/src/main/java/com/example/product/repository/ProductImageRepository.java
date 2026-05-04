package com.example.product.repository;

import com.example.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    @Query("SELECT COALESCE(MAX(pi.sortOrder), 0) FROM ProductImage pi WHERE pi.productId = :productId")
    int findMaxSortOrderByProductId(@Param("productId") Long productId);

    void deleteByProductId(Long productId);

    Optional<ProductImage> findFirstByProductIdOrderBySortOrderAsc(Long productId);
}
