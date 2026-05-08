package com.example.notification.repository;

import com.example.notification.entity.ConsoleNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConsoleNotificationRepository extends JpaRepository<ConsoleNotification, Long> {

    Optional<ConsoleNotification> findFirstByPayloadHashAndSourceJobOrderByCreatedAtDesc(
            String payloadHash, String sourceJob);

    List<ConsoleNotification> findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc(
            String tag);

    List<ConsoleNotification> findBySuppressedTrueAndDigestSentAtIsNullAndCreatedAtBefore(
            LocalDateTime threshold);

    /**
     * Step 4-5: 年次アーカイブ対象を抽出（設計書 §3.3 ③）。
     * <ul>
     *   <li>read_at IS NOT NULL かつ read_at < :readThreshold（既読から 1 年経過）</li>
     *   <li>suppressed = TRUE かつ digest_sent_at IS NOT NULL かつ digest_sent_at < :digestThreshold</li>
     *   <li>created_at < :forcedThreshold（無条件 1 年経過。抑制中で digest_sent_at IS NULL もここで救済）</li>
     * </ul>
     */
    @Query("""
            SELECT n FROM ConsoleNotification n
             WHERE (n.readAt IS NOT NULL AND n.readAt < :readThreshold)
                OR (n.suppressed = TRUE AND n.digestSentAt IS NOT NULL AND n.digestSentAt < :digestThreshold)
                OR (n.createdAt < :forcedThreshold)
            """)
    List<ConsoleNotification> findArchiveCandidates(
            @Param("readThreshold")    LocalDateTime readThreshold,
            @Param("digestThreshold") LocalDateTime digestThreshold,
            @Param("forcedThreshold") LocalDateTime forcedThreshold);

    /**
     * Step 6-0: Console 通知センター取得（設計書 §13.7.1 / §13.7.2）。
     * <ul>
     *   <li>表示条件：target_user_id = :userId  または  target_subscription_tag IN :tags</li>
     *   <li>suppressed = false（ダイジェスト経路で吸収済を除外）</li>
     *   <li>includeRead = false なら read_by_user_id IS NULL のみ</li>
     *   <li>level / tag フィルタは NULL のときフィルタしない</li>
     * </ul>
     * 購読タグ未登録ユーザのため、{@code tags} は空コレクションを許容する。
     * 空のとき `target_subscription_tag IN ()` は MySQL/H2 共に構文エラーとなるため、
     * クエリ側で {@code (:tagsEmpty = true OR target_subscription_tag IN :tags)}
     * のような形は取れない。空のときは別オーバーロード（target_user_id のみ）を呼ぶ運用とする。
     */
    @Query("""
            SELECT n FROM ConsoleNotification n
             WHERE n.suppressed = FALSE
               AND (:includeRead = TRUE OR n.readByUserId IS NULL)
               AND (:level IS NULL OR n.level = :level)
               AND (:tagFilter IS NULL OR n.targetSubscriptionTag = :tagFilter)
               AND (n.targetUserId = :userId OR n.targetSubscriptionTag IN :tags)
            """)
    Page<ConsoleNotification> searchVisible(@Param("userId") Long userId,
                                            @Param("tags") Collection<String> tags,
                                            @Param("level") String level,
                                            @Param("tagFilter") String tagFilter,
                                            @Param("includeRead") boolean includeRead,
                                            Pageable pageable);

    /** タグ購読が空のユーザ向け：target_user_id 一致のみで絞る。 */
    @Query("""
            SELECT n FROM ConsoleNotification n
             WHERE n.suppressed = FALSE
               AND (:includeRead = TRUE OR n.readByUserId IS NULL)
               AND (:level IS NULL OR n.level = :level)
               AND (:tagFilter IS NULL OR n.targetSubscriptionTag = :tagFilter)
               AND n.targetUserId = :userId
            """)
    Page<ConsoleNotification> searchVisibleNoTags(@Param("userId") Long userId,
                                                  @Param("level") String level,
                                                  @Param("tagFilter") String tagFilter,
                                                  @Param("includeRead") boolean includeRead,
                                                  Pageable pageable);
}
