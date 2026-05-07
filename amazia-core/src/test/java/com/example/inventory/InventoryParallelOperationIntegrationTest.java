package com.example.inventory;

import com.example.address.entity.Address;
import com.example.address.repository.AddressRepository;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.delivery.service.DeliveryStatusTransitionService;
import com.example.inbound.dto.RegisterInboundRequest;
import com.example.inbound.entity.Inbound;
import com.example.inbound.repository.InboundRepository;
import com.example.inbound.service.RegisterInboundService;
import com.example.inventory.entity.Inventories;
import com.example.inventory.repository.InventoriesRepository;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.order.dto.ConfirmOrderRequest;
import com.example.order.service.OrderConfirmationService;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.salesreturn.entity.SalesReturn;
import com.example.salesreturn.repository.SalesReturnRepository;
import com.example.salesreturn.service.ApproveSalesReturnService;
import com.example.salesreturn.service.RefundSalesReturnService;
import com.example.salesreturn.service.RequestSalesReturnService;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ15 Step E-α: 並行運用整合性テスト（RRRR-7）。
 *
 * <p>不変条件：任意時点で
 * {@code SUM(product_sku_stocks.quantity by productId) == inventories.quantity (warehouse=1)}
 * が成立する（auto-provision の初期値が SUM(SKU stock) のため、フックが正しく動けば一致が維持される）。
 *
 * <p>本テストは以下の 4 経路 + 例外ロールバックを実機検証する：
 *   1. 入荷登録（RegisterInboundService.register）
 *   2. 販売（OrderConfirmationService.confirm の通常購入）
 *   3. 返品復元（RefundSalesReturnService.refund）
 *   4. 予約購入の出荷時減算（DeliveryStatusTransitionService.transition PENDING→SHIPPED）
 *   5. 入荷登録の途中で例外発生時、inbounds・inventories・SKU stock がすべてロールバック
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class InventoryParallelOperationIntegrationTest {

    @Autowired private OrderConfirmationService orderConfirmationService;
    @Autowired private RegisterInboundService registerInboundService;
    @Autowired private RequestSalesReturnService requestSalesReturnService;
    @Autowired private ApproveSalesReturnService approveSalesReturnService;
    @Autowired private RefundSalesReturnService refundSalesReturnService;
    @Autowired private DeliveryStatusTransitionService deliveryStatusTransitionService;

    @Autowired private CustomerRepository customerRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private ProductSkuPriceRepository skuPriceRepository;
    @Autowired private InventoriesRepository inventoriesRepository;
    @Autowired private InboundRepository inboundRepository;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private SalesReturnRepository salesReturnRepository;

    @Value("${amazia.sales.payment-methods.credit-card-id}")
    private long creditCardId;

    @Value("${amazia.delivery.shipping-methods.home-delivery-id}")
    private long homeDeliveryMethodId;

    @Value("${amazia.delivery.default-warehouse-id}")
    private long defaultWarehouseId;

    @Value("${amazia.sales.shipping-statuses.shipped-id}")
    private long shippedId;

    private Long actorUserId;
    private Long customerId;
    private Long productId;
    private Long skuId;

    @BeforeEach
    void setUp() {
        actorUserId = 1L;

        Customer c = new Customer();
        c.setNameLast("並行運用");
        c.setNameFirst("テスト");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("parallel-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummy");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        customerId = customerRepository.save(c).getId();

        Product p = new Product();
        p.setName("並行運用テスト商品");
        p.setStatusCode("ON_SALE");
        productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-PRL-" + System.nanoTime());
        sku.setColor("白");
        sku.setSize("M");
        sku.setStatus("ACTIVE");
        skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(20);
        skuStockRepository.save(stock);

        ProductSkuPrice price = new ProductSkuPrice();
        price.setSkuId(skuId);
        price.setPrice(3000);
        skuPriceRepository.save(price);

        // 並行運用：inventories 行を事前投入（schema.sql の起動時マイグレーション
        // 「INSERT IGNORE INTO inventories ... SELECT id, 1, COALESCE(stock,0), ... FROM products」
        // の挙動を H2 テスト環境で再現する）。
        // SUM(SKU stock)=20 と一致する初期値で投入することで不変条件の起点を整える。
        Inventories inv = new Inventories();
        inv.setProductId(productId);
        inv.setWarehouseId(defaultWarehouseId);
        inv.setQuantity(20);
        inventoriesRepository.saveAndFlush(inv);
    }

    // ---- 1. 販売経路 ----

    @Test
    void 販売_注文確定後に_inventories_と_SKU_stock_合計が一致する() {
        confirmOrder(2, false);

        assertInvariantHolds();
        // 在庫値の検証（販売前 SUM=20, 販売 -2 → SUM=18, inventories=18）
        assertSkuStockSum(18);
        assertInventoriesQuantity(18);
    }

    @Test
    void 販売を複数回繰り返しても整合性が維持される() {
        confirmOrder(1, false);
        confirmOrder(3, false);
        confirmOrder(2, false);

        assertInvariantHolds();
        assertSkuStockSum(14);
        assertInventoriesQuantity(14);
    }

    // ---- 2. 入荷経路 ----

    @Test
    void 入荷登録後に_inventories_と_SKU_stock_合計が一致する() {
        registerInbound(5);

        assertInvariantHolds();
        assertSkuStockSum(25);
        assertInventoriesQuantity(25);
    }

    @Test
    void 販売_入荷_販売の混合シナリオでも整合性が維持される() {
        confirmOrder(2, false);   // 20 → 18
        registerInbound(10);      // 18 → 28
        confirmOrder(5, false);   // 28 → 23

        assertInvariantHolds();
        assertSkuStockSum(23);
        assertInventoriesQuantity(23);
    }

    // ---- 3. 返品復元経路 ----

    @Test
    void 返品復元_REFUNDED_遷移後に_inventories_と_SKU_stock_合計が一致する() {
        // 1. 注文確定（在庫 -3）
        Sales sales = confirmOrder(3, false);
        // 2. 配送を DELIVERED まで進める（返品申請の前提条件）
        Delivery delivery = deliveryRepository.findBySalesId(sales.getId()).orElseThrow();
        // 直接 DB 経由でステータスを進める（本テストの主眼は返品時の在庫整合性）
        delivery.setShippingStatusId(3L);  // DELIVERED
        deliveryRepository.save(delivery);
        // sales 側のステータスも DELIVERED に進める（RequestSalesReturn が DELIVERED 必須）
        com.example.sales.entity.Sales freshSales = inboundRepository == null ? null : null; // unused
        sales.setShippingStatusId(3L);
        // sales 直接保存
        // ... ApproveSalesReturn で sales を更新するため、ここでは保存だけ

        // SalesRepository 直接保存のため Inject 経由でやる
        // テスト簡易化のため deliveryStatusTransitionService 経由でなく直接更新
        // 以下、返品申請 → 承認 → 返金完了
        SalesReturn req = requestSalesReturnService.request(customerId,
                buildSalesReturnRequest(sales.getId(), 3));
        approveSalesReturnService.approve(req.getId(), actorUserId);
        refundSalesReturnService.refund(req.getId(), actorUserId);

        // 在庫が +3 で復元されて 20 に戻る
        assertInvariantHolds();
        assertSkuStockSum(20);
        assertInventoriesQuantity(20);
    }

    // ---- 4. 予約購入の出荷時減算経路 ----

    @Test
    void 予約購入の出荷時減算後に_inventories_と_SKU_stock_合計が一致する() {
        // 予約購入（注文時は減算しない）
        Sales sales = confirmOrder(2, true);
        // 注文時は SUM(SKU stock)=20 のまま
        assertInvariantHolds();
        assertSkuStockSum(20);
        assertInventoriesQuantity(20);

        // PENDING → SHIPPED で減算
        Delivery delivery = deliveryRepository.findBySalesId(sales.getId()).orElseThrow();
        deliveryStatusTransitionService.transition(delivery.getId(), shippedId, actorUserId);

        // SKU stock 20 → 18 / inventories も 20 → 18
        assertInvariantHolds();
        assertSkuStockSum(18);
        assertInventoriesQuantity(18);
    }

    // ---- 5. 例外時のロールバック ----

    @Test
    void 入荷登録時に異常リクエストでロールバックされる_整合性は維持される() {
        // 初期状態：SKU stock=20, inventories は applyDelta 経由で auto-provision されている可能性あり。
        // まず正常な registerInbound を 1 回行って inventories 行を確実に作成・整合性を確立。
        registerInbound(5);
        assertInvariantHolds();
        long inboundCountBefore = inboundRepository.count();
        int skuSumBefore = sumSkuStockByProductId();

        // 異常系：存在しない skuId で入荷登録 → 404 で全ロールバック
        RegisterInboundRequest bad = new RegisterInboundRequest();
        bad.setProductId(productId);
        bad.setSkuId(999_999L); // 存在しない
        bad.setQuantity(7);
        bad.setInboundedAt(LocalDate.of(2026, 5, 7));

        assertThrows(ResponseStatusException.class,
                () -> registerInboundService.register(bad, actorUserId));

        // ロールバック：inbounds 件数も SKU 在庫合計も変動なし
        assertEquals(inboundCountBefore, inboundRepository.count(),
                "例外時は inbounds の INSERT もロールバックされる");
        assertEquals(skuSumBefore, sumSkuStockByProductId(),
                "例外時は SKU 在庫加算もロールバックされる");
        assertInvariantHolds();
    }

    // ---- helpers ----

    /**
     * 不変条件：SUM(product_sku_stocks.quantity by productId) == inventories.quantity (warehouse=1)
     *
     * <p>本テストの setUp で inventories 行を事前投入（並行運用マイグレーション挙動の再現）
     * しているため、各経路後も行は存在する前提。
     */
    private void assertInvariantHolds() {
        int skuSum = sumSkuStockByProductId();
        Inventories inv = inventoriesRepository
                .findByProductIdAndWarehouseId(productId, defaultWarehouseId)
                .orElseThrow(() -> new AssertionError("inventories 行が存在しない（setUp で投入済みのはず）"));
        assertEquals(skuSum, inv.getQuantity().intValue(),
                "SUM(SKU stock) と inventories.quantity が一致するはず");
    }

    private int sumSkuStockByProductId() {
        return (int) skuStockRepository.sumQuantityByProductId(productId);
    }

    private void assertSkuStockSum(int expected) {
        assertEquals(expected, sumSkuStockByProductId(), "SKU 在庫合計の期待値");
    }

    private void assertInventoriesQuantity(int expected) {
        Inventories inv = inventoriesRepository
                .findByProductIdAndWarehouseId(productId, defaultWarehouseId)
                .orElseThrow(() -> new AssertionError("inventories 行が存在しない"));
        assertEquals(expected, inv.getQuantity().intValue(),
                "inventories.quantity の期待値");
    }

    private Sales confirmOrder(int quantity, boolean preorder) {
        ConfirmOrderRequest req = new ConfirmOrderRequest();
        req.setSkuId(skuId);
        req.setQuantity(quantity);
        req.setPaymentMethodId(creditCardId);
        req.setShippingMethodId(homeDeliveryMethodId);
        req.setPreorder(preorder);
        return orderConfirmationService.confirm(customerId, req);
    }

    private void registerInbound(int quantity) {
        RegisterInboundRequest req = new RegisterInboundRequest();
        req.setProductId(productId);
        req.setSkuId(skuId);
        req.setQuantity(quantity);
        req.setInboundedAt(LocalDate.of(2026, 5, 7));
        registerInboundService.register(req, actorUserId);
    }

    private com.example.salesreturn.dto.RequestSalesReturnRequest buildSalesReturnRequest(
            Long salesId, int quantity) {
        com.example.salesreturn.dto.RequestSalesReturnRequest r =
                new com.example.salesreturn.dto.RequestSalesReturnRequest();
        r.setSalesId(salesId);
        r.setQuantity(quantity);
        r.setReason("整合性テスト");
        return r;
    }
}
