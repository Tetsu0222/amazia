package com.example.sku.repository;

import com.example.sku.entity.ProductSkuImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductSkuImageRepository extends JpaRepository<ProductSkuImage, Long> {

    List<ProductSkuImage> findBySkuIdOrderBySortOrderAsc(Long skuId);

    @Query("SELECT COALESCE(MAX(i.sortOrder), 0) FROM ProductSkuImage i WHERE i.skuId = :skuId")
    int findMaxSortOrderBySkuId(@Param("skuId") Long skuId);
}
