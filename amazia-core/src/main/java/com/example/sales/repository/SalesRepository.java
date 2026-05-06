package com.example.sales.repository;

import com.example.sales.entity.Sales;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalesRepository extends JpaRepository<Sales, Long> {
    List<Sales> findByUserIdOrderBySalesDateDesc(Long userId);
    Optional<Sales> findByPaymentId(String paymentId);
}
