package com.example.salesreturn;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.order.dto.ConfirmOrderRequest;
import com.example.order.service.OrderConfirmationService;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.salesreturn.dto.RequestSalesReturnRequest;
import com.example.salesreturn.service.ApproveSalesReturnService;
import com.example.salesreturn.service.RefundSalesReturnService;
import com.example.salesreturn.service.RejectSalesReturnService;
import com.example.salesreturn.service.RequestSalesReturnService;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ14 Step B-5-8: 返品ワークフローの operation_logs 記録を検証する。
 *
 * 設計書 r4 / docs/ai_context/operation_logs_naming.md。
 * 承認・却下・返金完了の各 Service が operation_logs に
 * action / target_type / target_id / screen_name / api_name / user_id を
 * 期待通りに記録することを検証する。
 *
 * Market 会員起点の返品申請（B-5-1）は operation_logs 対象外（user_id が
 * users.id 参照のため market_customers.id は格納できない設計）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class SalesReturnOperationLogTest {

    @Autowired private ApproveSalesReturnService approveService;
    @Autowired private RejectSalesReturnService rejectService;
    @Autowired private RefundSalesReturnService refundService;
    @Autowired private RequestSalesReturnService requestService;
    @Autowired private OrderConfirmationService orderService;
    @Autowired private SalesRepository salesRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private ProductSkuPriceRepository skuPriceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OperationLogRepository operationLogRepository;

    @Value("${amazia.sales.payment-methods.credit-card-id}")
    private long creditCardId;
    @Value("${amazia.sales.shipping-statuses.delivered-id}")
    private long deliveredStatusId;

    @Test
    void 承認時に_operation_logs_が記録される() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("赤", "M", 10, 3000);
        Long salesId = confirmAndDeliver(customerId, skuId, 2);
        Long returnId = requestReturn(customerId, salesId, 1);

        approveService.approve(returnId, approverId);

        OperationLog log = findLatestForTarget(returnId);
        assertEquals("approve_sales_return", log.getAction());
        assertEquals("sales_return", log.getTargetType());
        assertEquals(returnId, log.getTargetId());
        assertEquals("console.sales_return.approve", log.getScreenName());
        assertEquals("POST /api/sales-returns/:id/approve", log.getApiName());
        assertEquals(approverId, log.getUserId());
    }

    @Test
    void 却下時に_operation_logs_が記録される() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("黒", "L", 5, 5000);
        Long salesId = confirmAndDeliver(customerId, skuId, 1);
        Long returnId = requestReturn(customerId, salesId, 1);

        rejectService.reject(returnId, approverId);

        OperationLog log = findLatestForTarget(returnId);
        assertEquals("reject_sales_return", log.getAction());
        assertEquals("sales_return", log.getTargetType());
        assertEquals(returnId, log.getTargetId());
        assertEquals("console.sales_return.approve", log.getScreenName());
        assertEquals("POST /api/sales-returns/:id/reject", log.getApiName());
        assertEquals(approverId, log.getUserId());
    }

    @Test
    void 返金完了時に_operation_logs_が記録される() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("青", "S", 5, 2000);
        Long salesId = confirmAndDeliver(customerId, skuId, 1);
        Long returnId = requestReturn(customerId, salesId, 1);

        approveService.approve(returnId, approverId);
        refundService.refund(returnId, approverId);

        // 承認と返金完了で 2 件記録される
        List<OperationLog> logs = operationLogRepository.findByTargetTypeAndTargetId("sales_return", returnId);
        assertEquals(2, logs.size(), "承認 + 返金完了で 2 件記録される");

        OperationLog refundLog = logs.stream()
                .filter(l -> "refund_sales_return".equals(l.getAction()))
                .findFirst()
                .orElseThrow();
        assertEquals("sales_return", refundLog.getTargetType());
        assertEquals(returnId, refundLog.getTargetId());
        assertEquals("console.sales_return.approve", refundLog.getScreenName());
        assertEquals("POST /api/sales-returns/:id/refund", refundLog.getApiName());
        assertEquals(approverId, refundLog.getUserId());
    }

    @Test
    void 状態遷移ガードで例外発生時は_operation_logs_は記録されない() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("白", "M", 5, 1000);
        Long salesId = confirmAndDeliver(customerId, skuId, 1);
        Long returnId = requestReturn(customerId, salesId, 1);

        approveService.approve(returnId, approverId);  // log 1 件

        // APPROVED 状態で再度 approve しようとすると CONFLICT
        try {
            approveService.approve(returnId, approverId);
            fail("CONFLICT 例外が発生するはず");
        } catch (Exception expected) {
            // 期待動作
        }

        // log は 1 件のままで増えない
        List<OperationLog> logs = operationLogRepository.findByTargetTypeAndTargetId("sales_return", returnId);
        assertEquals(1, logs.size(), "ガード違反では log は記録されない");
    }

    // ---- helpers -------------------------------------------------------

    private OperationLog findLatestForTarget(Long returnId) {
        List<OperationLog> logs = operationLogRepository.findByTargetTypeAndTargetId("sales_return", returnId);
        assertFalse(logs.isEmpty(), "operation_logs に少なくとも 1 件記録されている");
        return logs.get(logs.size() - 1);
    }

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
        c.setEmail("opl-" + System.nanoTime() + "@example.com");
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
        p.setName("操作ログテスト商品");
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
