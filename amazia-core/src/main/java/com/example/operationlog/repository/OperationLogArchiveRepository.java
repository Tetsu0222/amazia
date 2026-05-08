package com.example.operationlog.repository;

import com.example.operationlog.entity.OperationLogArchive;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationLogArchiveRepository extends JpaRepository<OperationLogArchive, Long> {
}
