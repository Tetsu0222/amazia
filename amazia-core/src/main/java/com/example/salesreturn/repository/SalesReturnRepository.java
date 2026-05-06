package com.example.salesreturn.repository;

import com.example.salesreturn.entity.SalesReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {
    List<SalesReturn> findBySalesId(Long salesId);
    List<SalesReturn> findByStatus(String status);
}
