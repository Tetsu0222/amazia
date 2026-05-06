package com.example.salesreturn;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.order.dto.ConfirmOrderRequest;
import com.example.order.service.OrderConfirmationService;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.salesreturn.dto.RequestSalesReturnRequest;
import com.example.salesreturn.entity.SalesReturn;
import com.example.salesreturn.repository.SalesReturnRepository;
import com.example.salesreturn.service.ApproveSalesReturnService;
import com.example.salesreturn.service.RefundSalesReturnService;
import com.example.salesreturn.service.RejectSalesReturnService;
import com.example.salesreturn.service.RequestSalesReturnService;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ14 Step B-5-3: 返金完了 + 在庫戻し Service の検証。Step B-5 の核心。
 *
 * 設計書 r4 phase14 §返品返金完了。
 * 在庫が +n で戻ること、transaction が type='return' / reference='sales_return' で記録されること、
 * sales.shipping_status が RETURNED に遷移すること、APPROVED 以外からの遷移が拒否されることを検証する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class RefundSalesReturnServiceTest {

    @Autowired private RefundSalesReturnService refundService;
    @Autowired private ApproveSalesReturnService approveService;
    @Autowired private RejectSalesReturnService rejectService;
    @Autowired private RequestSalesReturnService requestService;
    @Autowired private OrderConfirmationService orderService;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesReturnRepository salesReturnRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private ProductSkuPriceRepository skuPriceRepository;
    @Autowired private ProductSkuStockTransactionRepository skuStockTxRepository;
    @Autowired private UserRepository userRepository;

    @Value("${amazia.sales.payment-methods.credit-card-id}")
    private long creditCardId;
    @Value("${amazia.sales.shipping-statuses.delivered-id}")
    private long deliveredStatusId;
    @Value("${amazia.sales.shipping-statuses.returned-id}")
    private long returnedStatusId;
    @Value("${amazia.sales.sku-stock-tx-types.return}")
    private String txTypeReturn;

    @Test
    void 返金完了で_REFUNDED_になり_在庫が戻り_transaction_が記録され_RETURNED_に遷移する() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("赤", "M", 10, 3000);

        // 通常購入: 在庫 10 → 8（2個購入）
        Long salesId = confirmAndDeliver(customerId, skuId, 2);
        int afterPurchase = skuStockRepository.findBySkuId(skuId).orElseThrow().getQuantity();
        assertEquals(8, afterPurchase);

        // 返品申請 → 承認 → 返金完了（数量1だけ返品）
        Long returnId = requestReturn(customerId, salesId, 1);
        approveService.approve(returnId, approverId);
        SalesReturn refunded = refundService.refund(returnId, approverId);

        // 1. 状態遷移
        assertEquals("REFUNDED", refunded.getStatus());
        assertEquals(approverId, refunded.getApproverId());

        // 2. 在庫戻し: 8 → 9
        int afterRefund = skuStockRepository.findBySkuId(skuId).orElseThrow().getQuantity();
        assertEquals(9, afterRefund);

        // 3. transaction 記録
        List<ProductSkuStockTransaction> txs = skuStockTxRepository.findBySkuIdOrderByCreatedAtDesc(skuId);
        ProductSkuStockTransaction returnTx = txs.stream()
                .filter(t -> txTypeReturn.equals(t.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("return type の transaction が記録されていない"));
        assertEquals(1, returnTx.getQuantity(), "+n で記録される");
        assertEquals("sales_return", returnTx.getReferenceType());
        assertEquals(refunded.getId(), returnTx.getReferenceId());
        assertEquals(approverId, returnTx.getCreatedByUserId());

        // 4. sales.shipping_status_id = RETURNED
        Sales sales = salesRepository.findById(salesId).orElseThrow();
        assertEquals(returnedStatusId, sales.getShippingStatusId());
    }

    @Test
    void 返品数量2で承認されたら在庫が_2_戻る() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("黒", "L", 10, 5000);

        Long salesId = confirmAndDeliver(customerId, skuId, 3);  // 在庫 10 → 7
        Long returnId = requestReturn(customerId, salesId, 2);
        approveService.approve(returnId, approverId);
        refundService.refund(returnId, approverId);

        int after = skuStockRepository.findBySkuId(skuId).orElseThrow().getQuantity();
        assertEquals(9, after, "在庫は 7 + 2 = 9 に戻る");
    }

    @Test
    void REQUESTED_を返金完了すると_CONFLICT() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("青", "S", 5, 2000);
        Long salesId = confirmAndDeliver(customerId, skuId, 1);
        Long returnId = requestReturn(customerId, salesId, 1);
        // 承認せずに返金完了を試みる

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> refundService.refund(returnId, approverId));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void REJECTED_を返金完了すると_CONFLICT() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("白", "M", 5, 1500);
        Long salesId = confirmAndDeliver(customerId, skuId, 1);
        Long returnId = requestReturn(customerId, salesId, 1);
        rejectService.reject(returnId, approverId);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> refundService.refund(returnId, approverId));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void REFUNDED_を再度返金完了すると_CONFLICT() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("緑", "L", 5, 2500);
        Long salesId = confirmAndDeliver(customerId, skuId, 1);
        Long returnId = requestReturn(customerId, salesId, 1);
        approveService.approve(returnId, approverId);
        refundService.refund(returnId, approverId);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> refundService.refund(returnId, approverId));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void 存在しない_id_を返金完了すると_NOT_FOUND() {
        Long approverId = createAdminUser();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> refundService.refund(999_999_999L, approverId));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ---- helpers -------------------------------------------------------

    private Long confirmAndDeliver(Long customerId, Long skuId, int quantity) {
        ConfirmOrderRequest req = new ConfirmOrderRequest();
        req.setSkuId(skuId);
        req.setQuantity(quantity);
        req.setPaymentMethodId(creditCardId);
        req.setShippingMethodId(1L);
        req.setPreorder(false);
        Long salesId = orderService.confirm(customerId, req).getId();
        Sales s = salesRepository.findById(salesId).orElseThrow();
        s.setShippingStatusId(deliveredStatusId);
        salesRepository.save(s);
        return salesId;
    }

    private Long requestReturn(Long customerId, Long salesId, int quantity) {
        RequestSalesReturnRequest req = new RequestSalesReturnRequest();
        req.setSalesId(salesId);
        req.setQuantity(quantity);
        return requestService.request(customerId, req).getId();
    }

    private Long createCustomer() {
        Customer c = new Customer();
        c.setNameLast("田中");
        c.setNameFirst("花子");
        c.setPostalCode("1500001");
        c.setAddress("東京都渋谷区神宮前1-1");
        c.setBirthday(LocalDate.of(1995, 5, 5));
        c.setEmail("rf-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummy");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        return customerRepository.save(c).getId();
    }

    private Long createAdminUser() {
        User u = new User();
        u.setEmployeeId("E" + (System.nanoTime() % 100000));
        u.setEmail("admin-" + System.nanoTime() + "@example.com");
        u.setName("管理者");
        u.setPasswordHash("dummy");
        u.setActiveFlag(true);
        return userRepository.save(u).getId();
    }

    private Long createSkuWithStockAndPrice(String color, String size, int quantity, int price) {
        Product p = new Product();
        p.setName("返品テスト商品");
        p.setDescription("テスト");
        p.setPrice(0);
        p.setStock(0);
        p.setStatusCode("ON_SALE");
        p.setPublishStart(LocalDateTime.now().minusDays(1));
        p.setPublishEnd(LocalDateTime.now().plusYears(1));
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-" + System.nanoTime());
        sku.setColor(color);
        sku.setSize(size);
        sku.setStatus("ACTIVE");
        Long skuId = skuRepository.save(sku).getId();

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(skuId);
        stock.setQuantity(quantity);
        skuStockRepository.save(stock);

        ProductSkuPrice priceEntity = new ProductSkuPrice();
        priceEntity.setSkuId(skuId);
        priceEntity.setPrice(price);
        skuPriceRepository.save(priceEntity);

        return skuId;
    }
}
