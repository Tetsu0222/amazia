package com.example.market.customer.repository;

import com.example.market.customer.entity.MarketSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface MarketSessionRepository extends JpaRepository<MarketSession, String> {

    @Transactional
    @Modifying
    @Query("DELETE FROM MarketSession s WHERE s.expiresAt < :now")
    int deleteAllExpired(@Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("DELETE FROM MarketSession s WHERE s.customerId = :customerId")
    int deleteByCustomerId(@Param("customerId") Long customerId);

    /**
     * sliding 用: last_accessed_at と expires_at を一括更新する。
     * Filter から直接呼ばれるため、自前のトランザクション境界を持たせる。
     */
    @Transactional
    @Modifying
    @Query("UPDATE MarketSession s SET s.lastAccessedAt = :now, s.expiresAt = :expiresAt WHERE s.sessionId = :sessionId")
    int touch(@Param("sessionId") String sessionId,
              @Param("now") LocalDateTime now,
              @Param("expiresAt") LocalDateTime expiresAt);
}
