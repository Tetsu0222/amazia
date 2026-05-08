package com.example.market.customer.repository;

import com.example.market.customer.entity.CustomerPasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CustomerPasswordResetTokenRepository extends JpaRepository<CustomerPasswordResetToken, Long> {
    Optional<CustomerPasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * 同一顧客の未使用トークンを一括 used=true に更新する。
     * パスワード再発行リクエストを連打されても直近の 1 通だけが有効になる運用にする。
     */
    @Modifying
    @Query("update CustomerPasswordResetToken t set t.used = true where t.customerId = :customerId and t.used = false")
    int invalidateActiveTokensByCustomerId(@Param("customerId") Long customerId);

    /**
     * フェーズ17 Step 3-5 / SessionAndTokenSweepJob 用：
     * 期限切れ または 使用済みのトークンを物理削除する。
     */
    @Transactional
    @Modifying
    @Query("delete from CustomerPasswordResetToken t where t.expiresAt < :now or t.used = true")
    int deleteExpiredOrUsed(@Param("now") LocalDateTime now);
}
