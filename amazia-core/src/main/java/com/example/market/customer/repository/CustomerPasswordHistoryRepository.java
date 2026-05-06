package com.example.market.customer.repository;

import com.example.market.customer.entity.CustomerPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerPasswordHistoryRepository extends JpaRepository<CustomerPasswordHistory, Long> {
    List<CustomerPasswordHistory> findTop5ByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
