package com.example.order;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.order.dto.ConfirmOrderRequest;
import com.example.order.dto.PurchaseHistoryItem;
import com.example.order.service.GetMyPurchaseHistoryService;
import com.example.order.service.OrderConfirmationService;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
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
 * フェーズ14 Step B-3: 購入履歴取得 Service の検証。
 *
 * 設計書 r4 Market §購入履歴の表示項目（商品名+色+サイズ・数量・金額・配送方法・配送ステータス・予約区分）を返すこと、
 * および他の会員の sales が混入しないことを検証する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class GetMyPurchaseHistoryServiceTest {

    @Autowired private GetMyPurchaseHistoryService service;
    @Autowired private OrderConfirmationService orderService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private ProductSkuPriceRepository skuPriceRepository;

    @Value("${amazia.sales.payment-methods.credit-card-id}")
    private long creditCardId;

    @Test
    void 自分の購入履歴のみ取得でき表示項目が揃う() {
        Long me = createCustomer();
        Long other = createCustomer();
        Long productId = createProduct("テストTシャツ");
        Long skuId = createSku(productId, "赤", "M", 10, 3000);

        // 自分が 2 回購入、他人が 1 回購入
        confirm(me, skuId, 1);
        confirm(me, skuId, 2);
        confirm(other, skuId, 1);

        List<PurchaseHistoryItem> mine = service.list(me);

        assertEquals(2, mine.size(), "自分の 2 件のみ取得される");
        // 表示項目（先頭1件で代表確認）
        PurchaseHistoryItem first = mine.get(0);
        assertNotNull(first.getSalesId());
        assertNotNull(first.getSalesDate());
        assertEquals(skuId, first.getSkuId());
        assertEquals("テストTシャツ", first.getProductName());
        assertEquals("赤", first.getColor());
        assertEquals("M", first.getSize());
        assertNotNull(first.getQuantity());
        assertNotNull(first.getAmount());
        assertEquals("PENDING", first.getShippingStatusCode());
        assertEquals(1L, first.getShippingMethodId());
        assertEquals(creditCardId, first.getPaymentMethodId());
        assertFalse(first.isPreorder());

        // フェーズ15 r5 / Step D：deliveries 由来情報がネストして含まれる
        assertNotNull(first.getDelivery(), "deliveries が同時生成されるため delivery は非 null");
        assertNotNull(first.getDelivery().getScheduledDate(),
                "通常購入では scheduled_date が算出される");
        assertNull(first.getDelivery().getShippedDate());
        assertNull(first.getDelivery().getDeliveredDate());
        assertNull(first.getDelivery().getTrackingCode());
    }

    @Test
    void 購入履歴がない会員には空配列を返す() {
        Long me = createCustomer();

        List<PurchaseHistoryItem> mine = service.list(me);

        assertTrue(mine.isEmpty());
    }

    @Test
    void 予約購入は_preorder_true_で返る() {
        Long me = createCustomer();
        Long productId = createProduct("予約商品");
        Long skuId = createSku(productId, "黒", "L", 0, 5000); // 在庫 0 でも予約購入は通る

        ConfirmOrderRequest req = new ConfirmOrderRequest();
        req.setSkuId(skuId);
        req.setQuantity(1);
        req.setPaymentMethodId(creditCardId);
        req.setShippingMethodId(1L);
        req.setPreorder(true);
        orderService.confirm(me, req);

        List<PurchaseHistoryItem> mine = service.list(me);
        assertEquals(1, mine.size());
        assertTrue(mine.get(0).isPreorder());

        // 予約購入では delivery.scheduledDate は NULL（入荷時に再計算 / RR-4 / RRR-4）
        assertNotNull(mine.get(0).getDelivery());
        assertNull(mine.get(0).getDelivery().getScheduledDate(),
                "予約購入では入荷待ちのため scheduled_date は NULL");
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

    private Long createCustomer() {
        Customer c = new Customer();
        c.setNameLast("田中");
        c.setNameFirst("花子");
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
