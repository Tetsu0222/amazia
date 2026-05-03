package com.example.product;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStatusRepository extends JpaRepository<ProductStatus, String> {
}
