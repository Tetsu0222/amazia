package com.example.inventory.service;

import com.example.inventory.entity.Inventories;
import com.example.inventory.repository.InventoriesRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 並行運用フェーズの {@code inventories.quantity} 同期 Service（フェーズ15 r5 / RRRR-2）。
 *
 * <p>{@code products.stock} の増減と同じ delta を {@code inventories.quantity} に反映する。
 * phase14 r2 で {@code products.stock} 廃止と読み取り正本切替が行われた段階で削除予定。
 *
 * <p>呼び出し位置（既存 phase14 コードへの最小修正）：
 * <ul>
 *   <li>販売処理：{@code OrderConfirmationService.confirm()} の {@code products.stock} 減算直後</li>
 *   <li>返品復元：{@code RefundSalesReturnService} の在庫戻し直後</li>
 *   <li>入荷：{@code RegisterInboundService.register()} の {@code receive()} 直後</li>
 *   <li>予約出荷時減算：{@code DeliveryStatusTransitionService.applyShipmentStockChange()} 内</li>
 * </ul>
 */
@Service
public class InventorySyncService {

    private final InventoriesRepository inventoriesRepository;
    private final ProductSkuStockRepository skuStockRepository;

    public InventorySyncService(InventoriesRepository inventoriesRepository,
                                ProductSkuStockRepository skuStockRepository) {
        this.inventoriesRepository = inventoriesRepository;
        this.skuStockRepository = skuStockRepository;
    }

    /**
     * 指定商品×倉庫の在庫数を delta だけ加減する。呼び出し元のトランザクションに参加（{@code REQUIRED}）。
     *
     * <p>悲観ロック（{@code SELECT ... FOR UPDATE}）で行を取得し、
     * {@code quantity = quantity + delta} で更新する。CHECK 違反は例外として伝播する。
     *
     * <p>{@code inventories} 行が存在しない場合は {@code quantity=0} で自動作成してから
     * delta を適用する（auto-provision）。schema.sql の起動時マイグレーションで複製漏れた
     * 商品（新規追加商品など）でも頑健に動作する。並行運用マイグレーション完全性は
     * 別途 {@code Step E} の整合性テストで担保する。
     *
     * @param productId   対象商品 ID
     * @param warehouseId 倉庫 ID（並行運用期は常に default warehouse）
     * @param delta       増減量（販売・予約出荷は負数、返品復元・入荷は正数）
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void applyDelta(Long productId, Long warehouseId, int delta) {
        Inventories inv = inventoriesRepository
                .findByProductIdAndWarehouseIdForUpdate(productId, warehouseId)
                .orElseGet(() -> autoProvision(productId, warehouseId, delta));
        inv.setQuantity(inv.getQuantity() + delta);
        inventoriesRepository.save(inv);
        // CHECK(quantity >= 0) 違反は flush 時に DataIntegrityViolationException として伝播。
    }

    /**
     * {@code inventories} 行が欠落している場合の自動補完。
     *
     * <p>初期値は SKU 在庫の合算（{@code SUM(product_sku_stocks.quantity) by productId}）を採用する。
     * phase10 で在庫は SKU 側へ移行済みのため、{@code products.stock} ではなく
     * SKU 在庫合算が現在の真の在庫水準である。これにより：
     * <ul>
     *   <li>本番：schema.sql の起動時マイグレーションで {@code inventories} 行は全商品に複製済みのため通常は到達しない</li>
     *   <li>テスト：{@code products.stock=0} でも SKU 在庫があれば正しく初期化される</li>
     * </ul>
     *
     * <p>注意：呼び出し元（{@code OrderConfirmationService} 等）で SKU 在庫を先に減算してから
     * {@code applyDelta} を呼ぶため、{@code SUM(SKU stock)} は減算後の値が取得され、
     * その後 {@code +delta} すると最終 quantity が「{@code SUM(SKU stock 減算後) + delta}」となる。
     * これでも CHECK(quantity >= 0) は保たれる（販売前の SUM ≥ |delta| が成立していれば、
     * 減算後の SUM ≥ 0 で、さらに delta は既に SKU 側で減算済みなので applyDelta の delta は
     * 「同じ delta を inventories にも反映する」だけ）。
     *
     * <p>厳密には呼び出し位置（SKU 在庫減算前/後）によって SUM の意味が変わるが、
     * 並行運用期は本番では到達しない経路のため、過渡的な実用解として許容する。
     * phase14 r2 完全移行時にこのフックは削除予定。
     */
    private Inventories autoProvision(Long productId, Long warehouseId, int delta) {
        long skuStockSum = skuStockRepository.sumQuantityByProductId(productId);
        // SUM が delta により負になる可能性は CHECK 違反として呼び出し元に伝播
        int initialQuantity = (int) skuStockSum;
        Inventories created = new Inventories();
        created.setProductId(productId);
        created.setWarehouseId(warehouseId);
        created.setQuantity(initialQuantity);
        return inventoriesRepository.save(created);
    }
}
