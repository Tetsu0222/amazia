package com.example.salesreturn.repository;

import com.example.salesreturn.entity.SalesReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {
    List<SalesReturn> findBySalesId(Long salesId);
    List<SalesReturn> findByStatus(String status);
    Optional<SalesReturn> findFirstBySalesIdAndStatusIn(Long salesId, Collection<String> statuses);
    List<SalesReturn> findAllByOrderByCreatedAtDescIdDesc();
}
