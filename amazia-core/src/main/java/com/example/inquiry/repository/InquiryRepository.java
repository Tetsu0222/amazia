package com.example.inquiry.repository;

import com.example.inquiry.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 問い合わせ親 Repository（フェーズ18 r3 / 設計書 §3.1）。
 */
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    /** ベルマーク件数（設計書 §6.3 真実の元 / status='NEW' の COUNT） */
    long countByStatus(String status);

    /**
     * Console 一覧（フィルタ：status / target_type / dateFrom / dateTo / userName 部分一致）。
     * RV-4：market_customers.name_last + ' ' + name_first を userName として LIKE 検索する。
     */
    @Query("""
            SELECT i FROM Inquiry i
              LEFT JOIN com.example.market.customer.entity.Customer mc
                ON mc.id = i.userId
             WHERE (:status IS NULL OR i.status = :status)
               AND (:targetType IS NULL OR i.targetType = :targetType)
               AND (:dateFrom IS NULL OR i.createdAt >= :dateFrom)
               AND (:dateTo   IS NULL OR i.createdAt <  :dateTo)
               AND (:userNameLike IS NULL
                    OR LOWER(CONCAT(mc.nameLast, ' ', mc.nameFirst))
                       LIKE LOWER(CONCAT('%', :userNameLike, '%')))
             ORDER BY i.updatedAt DESC
            """)
    Page<Inquiry> searchForConsole(@Param("status") String status,
                                   @Param("targetType") String targetType,
                                   @Param("dateFrom") LocalDateTime dateFrom,
                                   @Param("dateTo") LocalDateTime dateTo,
                                   @Param("userNameLike") String userNameLike,
                                   Pageable pageable);

    /** Market 一覧（自分のみ強制） */
    Page<Inquiry> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);
}
