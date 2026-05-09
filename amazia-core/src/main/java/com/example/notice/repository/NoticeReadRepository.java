package com.example.notice.repository;

import com.example.notice.entity.NoticeRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * お知らせ既読履歴 Repository（フェーズ19 r2 / 設計書 §DB 設計）。
 *
 * <p>(notice_id, market_customer_id) UNIQUE のため、重複登録は冪等扱い。
 * Step A2 の MarkAsReadService では「exists 確認 → false なら INSERT」方式で
 * H2 / MySQL 双方の互換性を確保する（ON DUPLICATE KEY UPDATE が H2 で未対応のため）。
 */
public interface NoticeReadRepository extends JpaRepository<NoticeRead, Long> {

    boolean existsByNoticeIdAndMarketCustomerId(Long noticeId, Long marketCustomerId);

    /**
     * 一覧 API の N+1 回避用。指定会員 × 指定 notice_id 群の既読 notice_id を 1 クエリで返す。
     */
    @Query("""
            SELECT r.noticeId FROM NoticeRead r
             WHERE r.marketCustomerId = :customerId
               AND r.noticeId IN :noticeIds
            """)
    List<Long> findReadNoticeIdsByCustomer(@Param("customerId") Long customerId,
                                           @Param("noticeIds") Collection<Long> noticeIds);
}
