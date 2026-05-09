package com.example.notice.repository;

import com.example.notice.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * お知らせ Repository（フェーズ19 r2 / 設計書 §DB 設計 / §機能詳細）。
 *
 * <p>一覧取得（公開期間フィルタ + LEFT JOIN notice_reads）と未読数集計は
 * Service 層で組み立てるが、N+1 を避けるために Repository 側に専用クエリを定義する。
 */
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** 削除済を除いた単件取得（Console 含むほぼ全用途で使用）。 */
    Optional<Notice> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 公開期間内かつ削除済でない単件取得（Market 詳細・既読登録の前段で使用）。
     *
     * <p>条件：deleted_at IS NULL かつ now BETWEEN publish_start AND publish_end（境界値は含む）。
     */
    @Query("""
            SELECT n FROM Notice n
             WHERE n.id = :id
               AND n.deletedAt IS NULL
               AND n.publishStart <= :now
               AND n.publishEnd   >= :now
            """)
    Optional<Notice> findByIdActiveAt(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * Market / 未認証向け一覧（公開期間内 + 未削除のみ / category_id 任意フィルタ）。
     *
     * <p>並び順は category_id ASC, publish_start DESC, id DESC（設計書 §機能詳細）。
     */
    @Query("""
            SELECT n FROM Notice n
             WHERE n.deletedAt IS NULL
               AND n.publishStart <= :now
               AND n.publishEnd   >= :now
               AND (:categoryId IS NULL OR n.categoryId = :categoryId)
             ORDER BY n.categoryId ASC, n.publishStart DESC, n.id DESC
            """)
    Page<Notice> searchActive(@Param("now") LocalDateTime now,
                              @Param("categoryId") Long categoryId,
                              Pageable pageable);

    /**
     * Console 向け一覧（include_unpublished / include_deleted の組み合わせ）。
     */
    @Query("""
            SELECT n FROM Notice n
             WHERE (:includeDeleted = TRUE OR n.deletedAt IS NULL)
               AND (:includeUnpublished = TRUE
                    OR (n.publishStart <= :now AND n.publishEnd >= :now))
               AND (:categoryId IS NULL OR n.categoryId = :categoryId)
             ORDER BY n.categoryId ASC, n.publishStart DESC, n.id DESC
            """)
    Page<Notice> searchForConsole(@Param("now") LocalDateTime now,
                                  @Param("categoryId") Long categoryId,
                                  @Param("includeUnpublished") boolean includeUnpublished,
                                  @Param("includeDeleted") boolean includeDeleted,
                                  Pageable pageable);

    /**
     * ヘッダー用未読お知らせ取得（GET /api/customer/notices/unread）。
     *
     * <p>未読 = notice_reads に当該会員の行が存在しない。
     * 公開期間内 + 未削除、category_id ASC → publish_start DESC で {@code limit} 件まで。
     */
    @Query("""
            SELECT n FROM Notice n
             WHERE n.deletedAt IS NULL
               AND n.publishStart <= :now
               AND n.publishEnd   >= :now
               AND NOT EXISTS (
                    SELECT 1 FROM com.example.notice.entity.NoticeRead r
                     WHERE r.noticeId = n.id
                       AND r.marketCustomerId = :customerId
               )
             ORDER BY n.categoryId ASC, n.publishStart DESC, n.id DESC
            """)
    List<Notice> findUnreadHeaderNotices(@Param("now") LocalDateTime now,
                                         @Param("customerId") Long customerId,
                                         Pageable pageable);

    /**
     * 未読数集計（GET /api/customer/notices/unread-count）。
     * 設計書 §6 の擬似 SQL を JPQL 化。category_code ごとに COUNT を返す。
     */
    @Query("""
            SELECT nc.code, COUNT(n)
              FROM Notice n
              JOIN com.example.notice.entity.NoticeCategory nc
                ON nc.id = n.categoryId
             WHERE n.deletedAt IS NULL
               AND n.publishStart <= :now
               AND n.publishEnd   >= :now
               AND NOT EXISTS (
                    SELECT 1 FROM com.example.notice.entity.NoticeRead r
                     WHERE r.noticeId = n.id
                       AND r.marketCustomerId = :customerId
               )
             GROUP BY nc.code
            """)
    List<Object[]> countUnreadByCategory(@Param("now") LocalDateTime now,
                                         @Param("customerId") Long customerId);
}
