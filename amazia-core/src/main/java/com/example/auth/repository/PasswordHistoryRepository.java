package com.example.auth.repository;

import com.example.auth.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {
    List<PasswordHistory> findTop3ByUserIdOrderByCreatedAtDesc(Long userId);
}
