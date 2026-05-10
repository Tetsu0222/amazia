package com.example.delivery.repository;

import com.example.delivery.entity.ShippingLeadTime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShippingLeadTimeRepository extends JpaRepository<ShippingLeadTime, Long> {

    /**
     * 配送方法×都道府県の厳密一致でリードタイムを取得する。
     * フェーズX-5 §設計上の注意：不一致時は呼び出し側で config フォールバック。
     */
    Optional<ShippingLeadTime> findByShippingMethodIdAndPrefecture(Long shippingMethodId, String prefecture);

    List<ShippingLeadTime> findByShippingMethodIdOrderByIdAsc(Long shippingMethodId);

    List<ShippingLeadTime> findAllByOrderByShippingMethodIdAscIdAsc();
}
