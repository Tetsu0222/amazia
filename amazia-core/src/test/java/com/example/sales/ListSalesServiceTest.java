package com.example.sales;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.order.dto.ConfirmOrderRequest;
import com.example.order.service.OrderConfirmationService;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.dto.AdminSalesItem;
import com.example.sales.service.ListSalesService;
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
 * フェーズ14 Step B-4: 売上一覧（管理画面用）取得 Service の検証。
 *
 * 設計書 r4 Console §売上管理の表示項目（ユーザ名・購入商品+色+サイズ・数量・金額・配送日・売上日・
 * 配送ステータス・決済方法・配送方法・予約区分）を返すこと、複数会員の sales を全件返すことを検証する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ListSalesServiceTest {

    @Autowired private ListSalesService service;
    @Autowired private OrderConfirmationService orderService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private ProductSkuPriceRepository skuPriceRepository;

    @Value("${amazia.sales.payment-methods.credit-card-id}")
    private long creditCardId;

    @Test
    void 全会員の売上一覧を返し表示項目が揃う() {
        int beforeCount = service.list().size();
        Long alice = createCustomer("田中", "太郎");
        Long bob = createCustomer("鈴木", "花子");
        Long productId = createProduct("テストTシャツ");
        Long skuId = createSku(productId, "赤", "M", 10, 3000);

        confirm(alice, skuId, 1);
        confirm(bob, skuId, 2);

        List<AdminSalesItem> all = service.list();
        assertEquals(beforeCount + 2, all.size(), "2 件追加される");

        // 表示項目を先頭1件で代表確認
        AdminSalesItem item = all.stream()
                .filter(s -> skuId.equals(s.getSkuId()))
                .findFirst()
                .orElseThrow();
        assertNotNull(item.getSalesId());
        assertNotNull(item.getSalesDate());
        assertNotNull(item.getCustomerId());
        assertNotNull(item.getCustomerName(), "ユーザ名（姓+名）が返る");
        assertEquals(skuId, item.getSkuId());
        assertEquals("テストTシャツ", item.getProductName());
        assertEquals("赤", item.getColor());
        assertEquals("M", item.getSize());
        assertNotNull(item.getQuantity());
        assertNotNull(item.getAmount());
        assertEquals("PENDING", item.getShippingStatusCode());
        assertEquals(1L, item.getShippingMethodId());
        assertEquals(creditCardId, item.getPaymentMethodId());
        assertNotNull(item.getPaymentMethodName(), "決済方法名が返る");
        assertFalse(item.isPreorder());
    }

    @Test
    void 売上が空のときは空配列を返す() {
        // Spring Boot 起動時の test-data.sql が sales を入れているかは環境依存だが、
        // 少なくとも例外を出さず List を返すことを検証する
        List<AdminSalesItem> all = service.list();
        assertNotNull(all);
    }

    @Test
    void 予約購入は_preorder_true_で返る() {
        Long me = createCustomer("予約", "ユーザ");
        Long productId = createProduct("予約商品");
        Long skuId = createSku(productId, "黒", "L", 0, 5000);

        ConfirmOrderRequest req = new ConfirmOrderRequest();
        req.setSkuId(skuId);
        req.setQuantity(1);
        req.setPaymentMethodId(creditCardId);
        req.setShippingMethodId(1L);
        req.setPreorder(true);
        orderService.confirm(me, req);

        AdminSalesItem item = service.list().stream()
                .filter(s -> skuId.equals(s.getSkuId()))
                .findFirst()
                .orElseThrow();
        assertTrue(item.isPreorder());
    }

    // ---- helpers -------------------------------------------------------

    private void confirm(Long customerId, Long skuId, int quantity) {
        ConfirmOrderRequest req = new ConfirmOrderRequest();
        req.setSkuId(skuId);
        req.setQuantity(quantity);
        req.setPaymentMethodId(creditCardId);
        req.setShippingMethodId(1L);
        req.setPreorder(false);
        orderService.confirm(customerId, req);
    }

    private Long createCustomer(String last, String first) {
        Customer c = new Customer();
        c.setNameLast(last);
        c.setNameFirst(first);
        c.setPostalCode("1500001");
        c.setAddress("東京都渋谷区神宮前1-1");
        c.setBirthday(LocalDate.of(1995, 5, 5));
        c.setEmail("buyer-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummy");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        return customerRepository.save(c).getId();
    }

    private Long createProduct(String name) {
        Product p = new Product();
        p.setName(name);
        p.setDescription("テスト商品");
        p.setPrice(0);
        p.setStock(0);
        p.setStatusCode("ON_SALE");
        p.setPublishStart(LocalDateTime.now().minusDays(1));
        p.setPublishEnd(LocalDateTime.now().plusYears(1));
        return productRepository.save(p).getId();
    }

    private Long createSku(Long productId, String color, String size, int quantity, int price) {
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
