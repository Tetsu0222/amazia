package com.example.inbound.repository;

import com.example.inbound.entity.Inbound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InboundRepository extends JpaRepository<Inbound, Long> {

    List<Inbound> findByProductIdOrderByInboundedAtDesc(Long productId);
}
