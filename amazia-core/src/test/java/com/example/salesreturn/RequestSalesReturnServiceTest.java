package com.example.salesreturn;

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

import com.example.salesreturn.service.RequestSalesReturnService;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ14 Step B-5-1: 返品申請 Service の検証。
 *
 * 設計書 r4 phase14 §返品申請。
 * 検証順（NOT_FOUND → CONFLICT → BAD_REQUEST → CONFLICT 重複）の挙動を網羅する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class RequestSalesReturnServiceTest {

    @Autowired private RequestSalesReturnService service;
    @Autowired private OrderConfirmationService orderService;
    @Autowired private SalesRepository salesRepository;
    @Autowired private SalesReturnRepository salesReturnRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private ProductSkuPriceRepository skuPriceRepository;

    @Value("${amazia.sales.payment-methods.credit-card-id}")
    private long creditCardId;
    @Value("${amazia.sales.shipping-statuses.delivered-id}")
    private long deliveredStatusId;

    @Test
    void DELIVERED_な_sales_に対して_REQUESTED_で_INSERT_される() {
        Long me = createCustomer();
        Long skuId = createSkuWithStockAndPrice("赤", "M", 10, 3000);
        Long salesId = confirm(me, skuId, 2);
        markDelivered(salesId);

        RequestSalesReturnRequest req = new RequestSalesReturnRequest();
        req.setSalesId(salesId);
        req.setQuantity(1);
        req.setReason("サイズが合わなかった");

        SalesReturn created = service.request(me, req);

        assertNotNull(created.getId());
        assertEquals("REQUESTED", created.getStatus());
        assertEquals(salesId, created.getSalesId());
        assertEquals(1, created.getQuantity());
        assertEquals("サイズが合わなかった", created.getReason());
        assertFalse(created.isNotifiedUser());
        assertFalse(created.isNotifiedAdmin());
    }

    @Test
    void 他人の_sales_は_NOT_FOUND_扱いで拒否される() {
        Long me = createCustomer();
        Long other = createCustomer();
        Long skuId = createSkuWithStockAndPrice("黒", "L", 10, 5000);
        Long salesId = confirm(other, skuId, 1);
        markDelivered(salesId);

        RequestSalesReturnRequest req = new RequestSalesReturnRequest();
        req.setSalesId(salesId);
        req.setQuantity(1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.request(me, req));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void 配送ステータスが_DELIVERED_でなければ_CONFLICT_で拒否される() {
        Long me = createCustomer();
        Long skuId = createSkuWithStockAndPrice("青", "S", 5, 2000);
        Long salesId = confirm(me, skuId, 1);
        // 出荷前（PENDING）のまま申請を試みる

        RequestSalesReturnRequest req = new RequestSalesReturnRequest();
        req.setSalesId(salesId);
        req.setQuantity(1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.request(me, req));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void quantity_が購入数を超えると_BAD_REQUEST_で拒否される() {
        Long me = createCustomer();
        Long skuId = createSkuWithStockAndPrice("白", "M", 10, 1000);
        Long salesId = confirm(me, skuId, 2);
        markDelivered(salesId);

        RequestSalesReturnRequest req = new RequestSalesReturnRequest();
        req.setSalesId(salesId);
        req.setQuantity(3);  // 購入は 2

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.request(me, req));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void 既に_REQUESTED_な_sales_return_があれば_CONFLICT_で拒否される() {
        Long me = createCustomer();
        Long skuId = createSkuWithStockAndPrice("緑", "L", 5, 4000);
        Long salesId = confirm(me, skuId, 2);
        markDelivered(salesId);

        RequestSalesReturnRequest first = new RequestSalesReturnRequest();
        first.setSalesId(salesId);
        first.setQuantity(1);
        service.request(me, first);

        RequestSalesReturnRequest second = new RequestSalesReturnRequest();
        second.setSalesId(salesId);
        second.setQuantity(1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.request(me, second));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void 過去に_REJECTED_な_sales_return_があっても再申請できる() {
        Long me = createCustomer();
        Long skuId = createSkuWithStockAndPrice("黄", "S", 5, 2500);
        Long salesId = confirm(me, skuId, 1);
        markDelivered(salesId);

        // 過去申請を REJECTED 状態で直接作成
        SalesReturn rejected = new SalesReturn();
        rejected.setSalesId(salesId);
        rejected.setStatus("REJECTED");
        rejected.setQuantity(1);
        rejected.setNotifiedUser(false);
        rejected.setNotifiedAdmin(false);
        salesReturnRepository.save(rejected);

        RequestSalesReturnRequest req = new RequestSalesReturnRequest();
        req.setSalesId(salesId);
        req.setQuantity(1);

        SalesReturn created = service.request(me, req);
        assertEquals("REQUESTED", created.getStatus());
    }

    // ---- helpers -------------------------------------------------------

    private Long confirm(Long customerId, Long skuId, int quantity) {
        ConfirmOrderRequest req = new ConfirmOrderRequest();
        req.setSkuId(skuId);
        req.setQuantity(quantity);
        req.setPaymentMethodId(creditCardId);
        req.setShippingMethodId(1L);
        req.setPreorder(false);
        return orderService.confirm(customerId, req).getId();
    }

    private void markDelivered(Long salesId) {
        Sales sales = salesRepository.findById(salesId).orElseThrow();
        sales.setShippingStatusId(deliveredStatusId);
        salesRepository.save(sales);
    }

    private Long createCustomer() {
        Customer c = new Customer();
        c.setNameLast("田中");
        c.setNameFirst("花子");
        c.setPostalCode("1500001");
        c.setAddress("東京都渋谷区神宮前1-1");
        c.setBirthday(LocalDate.of(1995, 5, 5));
        c.setEmail("ret-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummy");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        return customerRepository.save(c).getId();
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
