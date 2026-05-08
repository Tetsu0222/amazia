package com.example.salesreport.repository;

import com.example.salesreport.entity.MonthlySalesReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MonthlySalesReportRepository extends JpaRepository<MonthlySalesReport, Long> {

    List<MonthlySalesReport> findByYearAndMonth(Short year, Short month);

    /**
     * Step 4-2: UPSERT 用の既存行検索（R-15）。NULL 軸も等値判定するため明示的に IS NULL を扱う。
     */
    @Query("""
            SELECT r FROM MonthlySalesReport r
             WHERE r.year = :year AND r.month = :month
               AND ((:productId IS NULL AND r.productId IS NULL) OR r.productId = :productId)
               AND ((:paymentMethodId IS NULL AND r.paymentMethodId IS NULL) OR r.paymentMethodId = :paymentMethodId)
               AND ((:shippingMethodId IS NULL AND r.shippingMethodId IS NULL) OR r.shippingMethodId = :shippingMethodId)
               AND ((:isPreorder IS NULL AND r.isPreorder IS NULL) OR r.isPreorder = :isPreorder)
            """)
    Optional<MonthlySalesReport> findByAxes(@Param("year") Short year,
                                            @Param("month") Short month,
                                            @Param("productId") Long productId,
                                            @Param("paymentMethodId") Long paymentMethodId,
                                            @Param("shippingMethodId") Long shippingMethodId,
                                            @Param("isPreorder") Boolean isPreorder);
}
