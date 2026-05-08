package com.example.salesreport.repository;

import com.example.salesreport.entity.MonthlySalesReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlySalesReportRepository extends JpaRepository<MonthlySalesReport, Long> {

    List<MonthlySalesReport> findByYearAndMonth(Short year, Short month);
}
