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
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ14 Step B-5-2: 返品承認 / 却下 Service の検証。
 *
 * 設計書 r4 phase14 §返品承認 §返品却下。
 * 承認時に sales.shipping_status が RETURN_REQUESTED に変わること、
 * 遷移ガードが REQUESTED 以外を拒否することを検証する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ApproveRejectSalesReturnServiceTest {

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
    @Autowired private UserRepository userRepository;

    @Value("${amazia.sales.payment-methods.credit-card-id}")
    private long creditCardId;
    @Value("${amazia.sales.shipping-statuses.delivered-id}")
    private long deliveredStatusId;
    @Value("${amazia.sales.shipping-statuses.return-requested-id}")
    private long returnRequestedStatusId;

    @Test
    void 承認すると_APPROVED_になり_配送ステータスが_RETURN_REQUESTED_に遷移する() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("赤", "M", 10, 3000);
        Long salesId = confirmAndDeliver(customerId, skuId, 2);
        Long returnId = requestReturn(customerId, salesId, 1);

        SalesReturn approved = approveService.approve(returnId, approverId);

        assertEquals("APPROVED", approved.getStatus());
        assertEquals(approverId, approved.getApproverId());
        assertNotNull(approved.getApprovedAt());

        Sales sales = salesRepository.findById(salesId).orElseThrow();
        assertEquals(returnRequestedStatusId, sales.getShippingStatusId(),
                "承認すると sales.shipping_status_id が RETURN_REQUESTED に更新される");
    }

    @Test
    void 却下すると_REJECTED_になり_配送ステータスは_DELIVERED_のまま() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("黒", "L", 5, 5000);
        Long salesId = confirmAndDeliver(customerId, skuId, 1);
        Long returnId = requestReturn(customerId, salesId, 1);

        SalesReturn rejected = rejectService.reject(returnId, approverId);

        assertEquals("REJECTED", rejected.getStatus());
        assertEquals(approverId, rejected.getApproverId());
        assertNotNull(rejected.getApprovedAt());

        Sales sales = salesRepository.findById(salesId).orElseThrow();
        assertEquals(deliveredStatusId, sales.getShippingStatusId(),
                "却下では sales.shipping_status_id は DELIVERED のまま");
    }

    @Test
    void 存在しない_id_を承認すると_NOT_FOUND() {
        Long approverId = createAdminUser();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> approveService.approve(999_999_999L, approverId));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void APPROVED_を再度承認すると_CONFLICT() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("青", "S", 5, 2000);
        Long salesId = confirmAndDeliver(customerId, skuId, 1);
        Long returnId = requestReturn(customerId, salesId, 1);

        approveService.approve(returnId, approverId);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> approveService.approve(returnId, approverId));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void REJECTED_を承認すると_CONFLICT() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("白", "M", 5, 1000);
        Long salesId = confirmAndDeliver(customerId, skuId, 1);
        Long returnId = requestReturn(customerId, salesId, 1);

        rejectService.reject(returnId, approverId);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> approveService.approve(returnId, approverId));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void APPROVED_を却下すると_CONFLICT() {
        Long customerId = createCustomer();
        Long approverId = createAdminUser();
        Long skuId = createSkuWithStockAndPrice("緑", "L", 5, 4000);
        Long salesId = confirmAndDeliver(customerId, skuId, 1);
        Long returnId = requestReturn(customerId, salesId, 1);

        approveService.approve(returnId, approverId);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> rejectService.reject(returnId, approverId));
        assertEquals(409, ex.getStatusCode().value());
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
        c.setEmail("ar-" + System.nanoTime() + "@example.com");
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
