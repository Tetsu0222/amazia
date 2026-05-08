package com.example.notification.repository;

import com.example.notification.entity.ConsoleNotificationArchive;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsoleNotificationArchiveRepository extends JpaRepository<ConsoleNotificationArchive, Long> {
}
