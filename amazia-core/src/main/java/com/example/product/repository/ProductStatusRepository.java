package com.example.product.repository;

import com.example.product.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStatusRepository extends JpaRepository<ProductStatus, String> {
}
