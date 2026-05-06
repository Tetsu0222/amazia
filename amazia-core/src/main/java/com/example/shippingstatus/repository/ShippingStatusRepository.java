package com.example.shippingstatus.repository;

import com.example.shippingstatus.entity.ShippingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShippingStatusRepository extends JpaRepository<ShippingStatus, Long> {
    Optional<ShippingStatus> findByCode(String code);
}
