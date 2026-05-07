package com.example.salesreturn.service;

import com.example.inventory.service.InventorySyncService;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.salesreturn.entity.SalesReturn;
import com.example.salesreturn.repository.SalesReturnRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 返金完了 Service（B-5-3 / 設計書 r4 phase14 §返品返金完了）。
 *
 * Step B-5 の核心。同一トランザクション内で：
 *   1. sales_return.status を APPROVED → REFUNDED に遷移
 *   2. product_sku_stocks.quantity += sales_return.quantity（@Version 楽観ロック）
 *   3. product_sku_stock_transactions に type='return', quantity=+n,
 *      reference_type='sales_return', reference_id=sales_return.id, created_by_user_id=管理者
 *      を記録
 *   4. sales.shipping_status を RETURNED に更新
 *   5. operation_logs に管理者操作を記録（B-5-8）
 *
 * 遷移ガード：APPROVED 以外（REQUESTED / REJECTED / REFUNDED）からの遷移は 409 CONFLICT。
 *
 * B-5-8 命名規約:
 *   action      : refund_sales_return
 *   target_type : sales_return
 *   target_id   : sales_return.id
 *   screen_name : console.sales_return.approve
 *   api_name    : POST /api/sales-returns/:id/refund
 */
@Service
public class RefundSalesReturnService {

    private static final String ACTION = "refund_sales_return";
    private static final String TARGET_TYPE = "sales_return";
    private static final String SCREEN_NAME = "console.sales_return.approve";
    private static final String API_NAME = "POST /api/sales-returns/:id/refund";

    private final SalesReturnRepository salesReturnRepository;
    private final SalesRepository salesRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductSkuStockRepository skuStockRepository;
    private final ProductSkuStockTransactionRepository skuStockTxRepository;
    private final InventorySyncService inventorySyncService;
    private final OperationLogRepository operationLogRepository;
    private final long returnedStatusId;
    private final long defaultWarehouseId;
    private final String txTypeReturn;

    public RefundSalesReturnService(SalesReturnRepository salesReturnRepository,
                                    SalesRepository salesRepository,
                                    ProductSkuRepository skuRepository,
                                    ProductSkuStockRepository skuStockRepository,
                                    ProductSkuStockTransactionRepository skuStockTxRepository,
                                    InventorySyncService inventorySyncService,
                                    OperationLogRepository operationLogRepository,
                                    @Value("${amazia.sales.shipping-statuses.returned-id}") long returnedStatusId,
                                    @Value("${amazia.delivery.default-warehouse-id}") long defaultWarehouseId,
                                    @Value("${amazia.sales.sku-stock-tx-types.return}") String txTypeReturn) {
        this.salesReturnRepository = salesReturnRepository;
        this.salesRepository = salesRepository;
        this.skuRepository = skuRepository;
        this.skuStockRepository = skuStockRepository;
        this.skuStockTxRepository = skuStockTxRepository;
        this.inventorySyncService = inventorySyncService;
        this.operationLogRepository = operationLogRepository;
        this.returnedStatusId = returnedStatusId;
        this.defaultWarehouseId = defaultWarehouseId;
        this.txTypeReturn = txTypeReturn;
    }

    @Transactional
    public SalesReturn refund(Long salesReturnId, Long approverUserId) {
        SalesReturn ret = salesReturnRepository.findById(salesReturnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sales_return not found"));

        if (!"APPROVED".equals(ret.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "sales_return cannot be refunded from status " + ret.getStatus());
        }

        Sales sales = salesRepository.findById(ret.getSalesId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sales not found"));

        // 1. ステータス遷移
        ret.setStatus("REFUNDED");
        // approver_id / approved_at は承認時に設定済み。返金完了で別承認者が処理する場合に備えて
        // approver_id は最後の操作者で上書きする運用とする。
        ret.setApproverId(approverUserId);
        SalesReturn saved = salesReturnRepository.save(ret);

        // 2. 在庫戻し（@Version 楽観ロック）
        ProductSkuStock stock = skuStockRepository.findBySkuId(sales.getSkuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "sku stock not registered"));
        stock.setQuantity(stock.getQuantity() + saved.getQuantity());
        skuStockRepository.save(stock);

        // 並行運用：inventories も同期加算（RRRR-2）
        ProductSku sku = skuRepository.findById(sales.getSkuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "sku not found"));
        inventorySyncService.applyDelta(sku.getProductId(), defaultWarehouseId,
                saved.getQuantity());

        // 3. transaction 記録
        ProductSkuStockTransaction tx = new ProductSkuStockTransaction();
        tx.setSkuId(sales.getSkuId());
        tx.setType(txTypeReturn);
        tx.setQuantity(saved.getQuantity());
        tx.setReferenceType("sales_return");
        tx.setReferenceId(saved.getId());
        tx.setCreatedByUserId(approverUserId);
        skuStockTxRepository.save(tx);

        // 4. sales.shipping_status_id = RETURNED
        sales.setShippingStatusId(returnedStatusId);
        salesRepository.save(sales);

        // 5. operation_logs 記録（B-5-8）
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
