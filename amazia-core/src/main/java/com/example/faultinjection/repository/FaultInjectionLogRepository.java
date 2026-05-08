package com.example.faultinjection.repository;

import com.example.faultinjection.entity.FaultInjectionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaultInjectionLogRepository extends JpaRepository<FaultInjectionLog, Long> {

    List<FaultInjectionLog> findByInjectorNameOrderByCreatedAtDesc(String injectorName);
}
