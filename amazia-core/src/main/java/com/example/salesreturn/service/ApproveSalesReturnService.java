package com.example.salesreturn.service;

import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.salesreturn.entity.SalesReturn;
import com.example.salesreturn.repository.SalesReturnRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * 返品承認 Service（B-5-2 / 設計書 r4 phase14 §返品承認）。
 *
 * status=REQUESTED の sales_return を APPROVED に遷移させ、対応する sales の
 * shipping_status を RETURN_REQUESTED に更新する（同一トランザクション）。
 *
 * 在庫戻しは行わない（返金完了時 = B-5-3 で実行）。
 *
 * 遷移ガード：REQUESTED 以外からの遷移は 409 CONFLICT。
 *
 * B-5-8: operation_logs に管理者操作を記録（命名規約は operation_logs_naming.md）。
 *   action      : approve_sales_return
 *   target_type : sales_return
 *   target_id   : sales_return.id
 *   screen_name : console.sales_return.approve
 *   api_name    : POST /api/sales-returns/:id/approve
 */
@Service
public class ApproveSalesReturnService {

    private static final String ACTION = "approve_sales_return";
    private static final String TARGET_TYPE = "sales_return";
    private static final String SCREEN_NAME = "console.sales_return.approve";
    private static final String API_NAME = "POST /api/sales-returns/:id/approve";

    private final SalesReturnRepository salesReturnRepository;
    private final SalesRepository salesRepository;
    private final OperationLogRepository operationLogRepository;
    private final long returnRequestedStatusId;

    public ApproveSalesReturnService(SalesReturnRepository salesReturnRepository,
                                     SalesRepository salesRepository,
                                     OperationLogRepository operationLogRepository,
                                     @Value("${amazia.sales.shipping-statuses.return-requested-id}") long returnRequestedStatusId) {
        this.salesReturnRepository = salesReturnRepository;
        this.salesRepository = salesRepository;
        this.operationLogRepository = operationLogRepository;
        this.returnRequestedStatusId = returnRequestedStatusId;
    }

    @Transactional
    public SalesReturn approve(Long salesReturnId, Long approverUserId) {
        SalesReturn ret = salesReturnRepository.findById(salesReturnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sales_return not found"));

        if (!"REQUESTED".equals(ret.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "sales_return cannot be approved from status " + ret.getStatus());
        }

        ret.setStatus("APPROVED");
        ret.setApproverId(approverUserId);
        ret.setApprovedAt(LocalDateTime.now());
        SalesReturn saved = salesReturnRepository.save(ret);

        Sales sales = salesRepository.findById(saved.getSalesId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sales not found"));
        sales.setShippingStatusId(returnRequestedStatusId);
        salesRepository.save(sales);

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
