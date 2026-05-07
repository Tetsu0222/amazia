package com.example.delivery.repository;

import com.example.delivery.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findBySalesId(Long salesId);

    List<Delivery> findByShippingStatusId(Long shippingStatusId);

    /**
     * 入荷再計算用：対象 {@code productId} に紐づく
     * {@code scheduled_date IS NULL} の {@code deliveries} を
     * {@code sales.created_at} 昇順 FIFO で取得（設計書 §入荷再計算ロジック）。
     *
     * <p>{@code sales} は {@code sku_id} を保持するため、
     * {@code sales JOIN product_skus ON sales.sku_id = product_skus.id} で
     * {@code product_skus.product_id} に集約する。
     */
    @Query("""
            select d from Delivery d, com.example.sales.entity.Sales s, com.example.sku.entity.ProductSku ps
            where s.id = d.salesId
              and ps.id = s.skuId
              and ps.productId = :productId
              and d.scheduledDate is null
            order by s.createdAt asc, d.id asc
            """)
    List<Delivery> findUnscheduledByProductIdOrderByCreatedAtAsc(@Param("productId") Long productId);
}
