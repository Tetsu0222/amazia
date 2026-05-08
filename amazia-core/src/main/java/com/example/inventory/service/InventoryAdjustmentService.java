package com.example.inventory.service;

import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * フェーズ17 Step 2-7 (H-4): SKU 在庫の adjust（手動補正）を一手に担う Service。
 *
 * <p>Service 層契約：
 * <ul>
 *   <li>{@code type = adjust} のときのみ {@code quantity != 0} を許容する</li>
 *   <li>その他の type（receive / sale / return / cancel など）は呼び出し側で {@code quantity > 0} を強制する</li>
 * </ul>
 *
 * <p>SKU TX を 1 行 INSERT したうえで、{@link ProductSkuStock#quantity} と
 * 並行運用フックの {@link InventorySyncService#applyDelta} に同じ delta を伝播する。
 * これにより SKU TX → SKU stock → inventories の三者が常に同期される。
 *
 * <p>H-4 / H-5 / H-6 / H-7 で使用される予定。コーディング規約 1-1（Service にロジック寄せ）と
 * 4-1（テスト値を {@code @Value} 経由で取得）を満たす。
 */
@Service
public class InventoryAdjustmentService {

    private final ProductSkuStockTransactionRepository txRepo;
    private final ProductSkuStockRepository skuStockRepo;
    private final ProductSkuRepository skuRepo;
    private final InventorySyncService inventorySync;

    @Value("${amazia.sales.sku-stock-tx-types.adjust}")
    private String adjustTypeValue;

    @Value("${amazia.delivery.default-warehouse-id}")
    private long defaultWarehouseId;

    public InventoryAdjustmentService(ProductSkuStockTransactionRepository txRepo,
                                      ProductSkuStockRepository skuStockRepo,
                                      ProductSkuRepository skuRepo,
                                      InventorySyncService inventorySync) {
        this.txRepo = txRepo;
        this.skuStockRepo = skuStockRepo;
        this.skuRepo = skuRepo;
        this.inventorySync = inventorySync;
    }

    /**
     * SKU 在庫を delta だけ補正し、SKU TX に {@code adjust} で 1 行残す。
     * 呼び出し元のトランザクションに参加する（既存トランザクションがあれば結合）。
     *
     * @param skuId         対象 SKU ID
     * @param quantity      補正量。0 は不可（H-4 符号契約）
     * @param referenceType SKU TX の reference_type（例：{@code bootstrap} / {@code fault_injection_compensation}）
     * @param referenceId   関連 ID（無ければ null）
     * @param userId        実行者ユーザ ID（バッチ起動なら null）
     * @param comment       SKU TX の comment
     * @return 永続化された SKU TX レコード
     */
    @Transactional
    public ProductSkuStockTransaction adjust(long skuId, int quantity, String referenceType,
                                             Long referenceId, Long userId, String comment) {
        if (quantity == 0) {
            throw new IllegalArgumentException("adjust quantity must not be 0");
        }

        ProductSku sku = skuRepo.findById(skuId)
                .orElseThrow(() -> new IllegalArgumentException("sku not found: " + skuId));

        ProductSkuStockTransaction tx = new ProductSkuStockTransaction();
        tx.setSkuId(skuId);
        tx.setType(adjustTypeValue);
        tx.setQuantity(quantity);
        tx.setReferenceType(referenceType);
        tx.setReferenceId(referenceId);
        tx.setCreatedByUserId(userId);
        tx.setComment(comment);
        ProductSkuStockTransaction saved = txRepo.save(tx);

        ProductSkuStock stock = skuStockRepo.findBySkuId(skuId)
                .orElseGet(() -> {
                    ProductSkuStock created = new ProductSkuStock();
                    created.setSkuId(skuId);
                    created.setQuantity(0);
                    return created;
                });
        stock.setQuantity(stock.getQuantity() + quantity);
        skuStockRepo.save(stock);

        inventorySync.applyDelta(sku.getProductId(), defaultWarehouseId, quantity);

        return saved;
    }
}
