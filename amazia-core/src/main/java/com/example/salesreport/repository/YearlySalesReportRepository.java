package com.example.salesreport.repository;

import com.example.salesreport.entity.YearlySalesReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface YearlySalesReportRepository extends JpaRepository<YearlySalesReport, Long> {

    List<YearlySalesReport> findByYear(Short year);

    /** Step 4-3: UPSERT 用の既存行検索（NULL 軸を含む）。 */
    @Query("""
            SELECT r FROM YearlySalesReport r
             WHERE r.year = :year
               AND ((:productId IS NULL AND r.productId IS NULL) OR r.productId = :productId)
               AND ((:paymentMethodId IS NULL AND r.paymentMethodId IS NULL) OR r.paymentMethodId = :paymentMethodId)
               AND ((:shippingMethodId IS NULL AND r.shippingMethodId IS NULL) OR r.shippingMethodId = :shippingMethodId)
               AND ((:isPreorder IS NULL AND r.isPreorder IS NULL) OR r.isPreorder = :isPreorder)
            """)
    Optional<YearlySalesReport> findByAxes(@Param("year") Short year,
                                           @Param("productId") Long productId,
                                           @Param("paymentMethodId") Long paymentMethodId,
                                           @Param("shippingMethodId") Long shippingMethodId,
                                           @Param("isPreorder") Boolean isPreorder);
}
