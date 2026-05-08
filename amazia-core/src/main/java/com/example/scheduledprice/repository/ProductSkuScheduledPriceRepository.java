package com.example.scheduledprice.repository;

import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductSkuScheduledPriceRepository extends JpaRepository<ProductSkuScheduledPrice, Long> {

    Optional<ProductSkuScheduledPrice> findFirstBySkuIdAndIsPendingTrue(Long skuId);

    List<ProductSkuScheduledPrice> findByApplyDateLessThanEqualAndIsPendingTrue(LocalDate today);

    List<ProductSkuScheduledPrice> findBySkuIdOrderByApplyDateDesc(Long skuId);
}
