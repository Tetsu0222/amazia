package com.example.salesreturn.service;

import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.salesreturn.entity.SalesReturn;
import com.example.salesreturn.repository.SalesReturnRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * 返品却下 Service（B-5-2 / 設計書 r4 phase14 §返品却下）。
 *
 * status=REQUESTED の sales_return を REJECTED に遷移させる。
 * 配送ステータスは変更しない（DELIVERED のまま、再申請は B-5-1 で許容）。
 *
 * 遷移ガード：REQUESTED 以外からの遷移は 409 CONFLICT。
 *
 * B-5-8: operation_logs に管理者操作を記録。
 *   action      : reject_sales_return
 *   target_type : sales_return
 *   target_id   : sales_return.id
 *   screen_name : console.sales_return.approve
 *   api_name    : POST /api/sales-returns/:id/reject
 */
@Service
public class RejectSalesReturnService {

    private static final String ACTION = "reject_sales_return";
    private static final String TARGET_TYPE = "sales_return";
    private static final String SCREEN_NAME = "console.sales_return.approve";
    private static final String API_NAME = "POST /api/sales-returns/:id/reject";

    private final SalesReturnRepository salesReturnRepository;
    private final OperationLogRepository operationLogRepository;

    public RejectSalesReturnService(SalesReturnRepository salesReturnRepository,
                                    OperationLogRepository operationLogRepository) {
        this.salesReturnRepository = salesReturnRepository;
        this.operationLogRepository = operationLogRepository;
    }

    @Transactional
    public SalesReturn reject(Long salesReturnId, Long approverUserId) {
        SalesReturn ret = salesReturnRepository.findById(salesReturnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sales_return not found"));

        if (!"REQUESTED".equals(ret.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "sales_return cannot be rejected from status " + ret.getStatus());
        }

        ret.setStatus("REJECTED");
        ret.setApproverId(approverUserId);
        ret.setApprovedAt(LocalDateTime.now());
        SalesReturn saved = salesReturnRepository.save(ret);

        recordOperationLog(approverUserId, saved.getId());

        return saved;
    }

    private void recordOperationLog(Long approverUserId, Long salesReturnId) {
        OperationLog log = new OperationLog();
        log.setUserId(approverUserId);
        log.setAction(ACTION);
        log.setTargetType(TARGET_TYPE);
        log.setTargetId(salesReturnId);
        log.setScreenName(SCREEN_NAME);
        log.setApiName(API_NAME);
        operationLogRepository.save(log);
    }
}
