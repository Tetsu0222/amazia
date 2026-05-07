package com.example.order.service;

import com.example.address.entity.Address;
import com.example.address.repository.AddressRepository;
import com.example.delivery.service.DeliveryCreationService;
import com.example.inventory.service.InventorySyncService;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.order.dto.ConfirmOrderRequest;
import com.example.order.exception.PaymentIdConflictException;
import com.example.payment.service.PaymentService;
import com.example.paymentmethod.repository.PaymentMethodRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.product.service.PreorderStatusService;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 注文確定 Service（フェーズ14 r4「注文確定フロー」擬似コード準拠）。
 *
 * 同一トランザクション内で以下を実行する：
 *   1. validateOrder：会員 / SKU / 商品公開 / 決済方法 / 配送方法 / 在庫予備チェック
 *   2. 配送先住所スナップショット作成（market_customers の現住所を address に INSERT）
 *   3. 通常購入の在庫減算（@Version 楽観ロック）と product_sku_stock_transactions 記録
 *   4. INSERT sales（payment_id UUID v7 採番、UNIQUE 違反時は冪等処理）
 *   5. SKU 在庫増減ログ reference_id を sales.id で更新
 *   6. （phase15 で DeliveryCreationService.createForSales を呼び出す。本フェーズではスタブ）
 *
 * 在庫モデル: 既存 product_sku_stocks（フェーズ10）を在庫の正本として活用（r4 / 設計書 §A）。
 */
@Service
public class OrderConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(OrderConfirmationService.class);

    private final CustomerRepository customerRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductSkuStockRepository skuStockRepository;
    private final ProductSkuStockTransactionRepository skuStockTransactionRepository;
    private final ProductSkuPriceRepository skuPriceRepository;
    private final ProductRepository productRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final AddressRepository addressRepository;
    private final SalesRepository salesRepository;
    private final PaymentService paymentService;
    private final PreorderStatusService preorderStatusService;
    private final DeliveryCreationService deliveryCreationService;
    private final InventorySyncService inventorySyncService;

    private final long pendingStatusId;
    private final long defaultWarehouseId;
    private final String txTypeSale;

    public OrderConfirmationService(
            CustomerRepository customerRepository,
            ProductSkuRepository skuRepository,
            ProductSkuStockRepository skuStockRepository,
            ProductSkuStockTransactionRepository skuStockTransactionRepository,
            ProductSkuPriceRepository skuPriceRepository,
            ProductRepository productRepository,
            PaymentMethodRepository paymentMethodRepository,
            AddressRepository addressRepository,
            SalesRepository salesRepository,
            PaymentService paymentService,
            PreorderStatusService preorderStatusService,
            DeliveryCreationService deliveryCreationService,
            InventorySyncService inventorySyncService,
            @Value("${amazia.sales.shipping-statuses.pending-id}") long pendingStatusId,
            @Value("${amazia.delivery.default-warehouse-id}") long defaultWarehouseId,
            @Value("${amazia.sales.sku-stock-tx-types.sale}") String txTypeSale) {
        this.customerRepository = customerRepository;
        this.skuRepository = skuRepository;
        this.skuStockRepository = skuStockRepository;
        this.skuStockTransactionRepository = skuStockTransactionRepository;
        this.skuPriceRepository = skuPriceRepository;
        this.productRepository = productRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.addressRepository = addressRepository;
        this.salesRepository = salesRepository;
        this.paymentService = paymentService;
        this.preorderStatusService = preorderStatusService;
        this.deliveryCreationService = deliveryCreationService;
        this.inventorySyncService = inventorySyncService;
        this.pendingStatusId = pendingStatusId;
        this.defaultWarehouseId = defaultWarehouseId;
        this.txTypeSale = txTypeSale;
    }

    @Transactional
    public Sales confirm(Long customerId, ConfirmOrderRequest request) {
        // 1. validateOrder
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "customer not found"));

        ProductSku sku = skuRepository.findById(request.getSkuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sku not found"));
        if (!"ACTIVE".equals(sku.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sku is not active");
        }

        Product product = productRepository.findById(sku.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
        // 設計書 phase14_5_preorder_status.md §2-2: 公開判定は JST 0:00 基準。
        // 旧 Product#isPublished() は秒単位 LocalDateTime 比較で PreorderStatusService と
        // 整合しないため、判定を統一窓口の PreorderStatusService に集約。
        if (!preorderStatusService.isPublished(product)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product is not published");
        }

        if (paymentMethodRepository.findById(request.getPaymentMethodId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payment method not found");
        }
        // shipping_methods マスタは phase15 で作成。Step B 段階では存在チェックを Service で省略し、
        // phase15 r5 完了時に shipping_methods.id 参照と FK 制約を有効化する。

        ProductSkuPrice price = skuPriceRepository.findBySkuId(sku.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "sku price not registered"));

        ProductSkuStock stock = skuStockRepository.findBySkuId(sku.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "sku stock not registered"));

        // 在庫予備チェック（is_preorder=false のときのみ）
        if (!request.isPreorder() && stock.getQuantity() < request.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "out of stock");
        }

        // 2. 配送先住所スナップショット作成（market_customers の現住所を address に INSERT）
        Address shippingAddress = createShippingAddressSnapshot(customer);

        // 3. 通常購入の在庫減算（@Version 楽観ロック）
        if (!request.isPreorder()) {
            stock.setQuantity(stock.getQuantity() - request.getQuantity());
            skuStockRepository.save(stock); // @Version で OptimisticLockException が起きうる
            // 並行運用：inventories も同期減算（RRRR-2）
            inventorySyncService.applyDelta(sku.getProductId(), defaultWarehouseId,
                    -request.getQuantity());
        }

        // 4. INSERT sales（payment_id 採番 + UNIQUE 違反時の冪等処理）
        int amount = price.getPrice() * request.getQuantity();
        String paymentId = paymentService.generatePaymentId();
        Sales sales = buildSales(customer.getId(), sku.getId(), request, amount, paymentId, shippingAddress.getId());

        Sales savedSales;
        try {
            savedSales = salesRepository.saveAndFlush(sales);
        } catch (DataIntegrityViolationException e) {
            // payment_id UNIQUE 違反 → 冪等処理判定（S14-5）
            savedSales = handlePaymentIdConflict(paymentId, customer.getId(), sku.getId(),
                    request.getQuantity(), amount, e);
        }

        // 5. 通常購入の場合、SKU 在庫増減ログを記録（reference_id = sales.id）
        if (!request.isPreorder()) {
            recordStockTransactionForSale(sku.getId(), request.getQuantity(),
                    savedSales.getId(), customer.getId());
        }

        // 6. phase15 r5 連携：deliveries 生成（過渡期シグネチャ / RRRR-3）
        //    phase14 r2 で sales.shipping_method_id 追加後は createForSales(savedSales.getId()) 単引数に移行。
        deliveryCreationService.createForSales(savedSales.getId(), request.getShippingMethodId());

        return savedSales;
    }

    private Address createShippingAddressSnapshot(Customer customer) {
        Address address = new Address();
        address.setUserId(customer.getId());
        address.setPostalCode(customer.getPostalCode());
        // r4: 会員住所は VARCHAR255 のため address_line にそのまま格納。prefecture/city/building は NULL
        address.setAddressLine(customer.getAddress());
        address.setActive(true);
        return addressRepository.save(address);
    }

    private Sales buildSales(Long userId, Long skuId, ConfirmOrderRequest request,
                             int amount, String paymentId, Long shippingAddressId) {
        Sales s = new Sales();
        s.setUserId(userId);
        s.setSkuId(skuId);
        s.setQuantity(request.getQuantity());
        s.setAmount(amount);
        s.setPaymentMethodId(request.getPaymentMethodId());
        s.setShippingMethodId(request.getShippingMethodId());
        s.setShippingAddressId(shippingAddressId);
        s.setShippingStatusId(pendingStatusId);
        s.setPaymentId(paymentId);
        s.setPreorder(request.isPreorder());
        s.setSalesDate(LocalDate.now());
        return s;
    }

    /**
     * payment_id UNIQUE 違反時の冪等処理（S14-5）。
     * user_id / sku_id / quantity / amount すべて一致時のみ既存 sales を返す。
     * 不一致なら例外を投げて拒否（決済 ID なりすまし対策）。
     */
    private Sales handlePaymentIdConflict(String paymentId, Long userId, Long skuId,
                                          int quantity, int amount,
                                          DataIntegrityViolationException original) {
        Optional<Sales> existing = salesRepository.findByPaymentId(paymentId);
        if (existing.isEmpty()) {
            // payment_id 以外の制約違反（ありえないはずだが防御的に再 throw）
            throw original;
        }
        Sales s = existing.get();
        if (s.getUserId().equals(userId)
                && s.getSkuId().equals(skuId)
                && s.getQuantity().equals(quantity)
                && s.getAmount().equals(amount)) {
            // 真の二重送信 → 既存 sales を返す（冪等扱い）
            return s;
        }
        log.error("payment_id reuse with mismatched fields: paymentId={}, userId={}, skuId={}, qty={}, amount={}",
                paymentId, userId, skuId, quantity, amount);
        throw new PaymentIdConflictException("payment_id reuse with mismatched fields");
    }

    private void recordStockTransactionForSale(Long skuId, int quantity, Long salesId, Long createdByUserId) {
        ProductSkuStockTransaction tx = new ProductSkuStockTransaction();
        tx.setSkuId(skuId);
        tx.setType(txTypeSale);
        tx.setQuantity(-quantity); // 販売は負数
        tx.setReferenceType("sales");
        tx.setReferenceId(salesId);
        tx.setCreatedByUserId(createdByUserId);
        skuStockTransactionRepository.save(tx);
    }
}
