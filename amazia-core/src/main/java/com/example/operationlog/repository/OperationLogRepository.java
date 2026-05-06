package com.example.operationlog.repository;

import com.example.operationlog.entity.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {
    List<OperationLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<OperationLog> findByTargetTypeAndTargetId(String targetType, Long targetId);
    List<OperationLog> findByActionOrderByCreatedAtDesc(String action);
    List<OperationLog> findAllByOrderByCreatedAtDescIdDesc();

    /**
     * Console 操作履歴画面の検索用。
     * screen_name / api_name の部分一致を OR で組み立てる（NULL は無視）。
     */
    @Query("""
            SELECT o FROM OperationLog o
            WHERE (:screenName IS NULL OR o.screenName LIKE CONCAT('%', :screenName, '%'))
              AND (:apiName    IS NULL OR o.apiName    LIKE CONCAT('%', :apiName,    '%'))
              AND (:action     IS NULL OR o.action     = :action)
            ORDER BY o.createdAt DESC, o.id DESC
            """)
    List<OperationLog> search(@Param("screenName") String screenName,
                              @Param("apiName") String apiName,
                              @Param("action") String action);
}
