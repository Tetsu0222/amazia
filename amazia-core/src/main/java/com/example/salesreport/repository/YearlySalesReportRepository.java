package com.example.salesreport.repository;

import com.example.salesreport.entity.YearlySalesReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YearlySalesReportRepository extends JpaRepository<YearlySalesReport, Long> {

    List<YearlySalesReport> findByYear(Short year);
}
