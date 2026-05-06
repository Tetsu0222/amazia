package com.example.salesreturn;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.order.dto.ConfirmOrderRequest;
import com.example.order.service.OrderConfirmationService;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.salesreturn.dto.AdminSalesReturnItem;
import com.example.salesreturn.dto.RequestSalesReturnRequest;
import com.example.salesreturn.service.ListSalesReturnService;
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
 * フェーズ14 Step B-5-4: 返品一覧（管理画面用）取得 Service の検証。
 *
 * 設計書 r4 phase14 §返品管理の表示項目（顧客名・商品名+色+サイズ・数量・状態・申請日・売上ID）を
 * 返すこと、複数顧客の返品を全件返すことを検証する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ListSalesReturnServiceTest {

    @Autowired private ListSalesReturnService service;
    @Autowired private RequestSalesReturnService requestService;
    @Autowired private OrderConfirmationService orderService;
    @Autowired private SalesRepository salesRepository;
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
    void 全顧客の返品申請一覧を返し表示項目が揃う() {
        int beforeCount = service.list().size();

        Long alice = createCustomer("田中", "太郎");
        Long bob = createCustomer("鈴木", "花子");
        Long aliceSku = createSkuWithStockAndPrice("赤", "M", 10, 3000);
        Long bobSku = createSkuWithStockAndPrice("黒", "L", 10, 5000);

        Long aliceSalesId = confirmAndDeliver(alice, aliceSku, 2);
        Long bobSalesId = confirmAndDeliver(bob, bobSku, 1);

        Long aliceReturnId = requestReturn(alice, aliceSalesId, 1, "サイズが合わなかった");
        requestReturn(bob, bobSalesId, 1, null);

        List<AdminSalesReturnItem> all = service.list();
        assertEquals(beforeCount + 2, all.size(), "2 件追加される");

        AdminSalesReturnItem item = all.stream()
                .filter(r -> aliceReturnId.equals(r.getId()))
                .findFirst()
                .orElseThrow();

        assertNotNull(item.getId());
        assertEquals("REQUESTED", item.getStatus());
        assertEquals(1, item.getQuantity());
        assertEquals("サイズが合わなかった", item.getReason());
        assertNotNull(item.getCreatedAt());
        assertNull(item.getApprovedAt(), "REQUESTED 段階では承認日時は null");
        assertNull(item.getApproverId());

        assertEquals(aliceSalesId, item.getSalesId());
        assertNotNull(item.getSalesDate());
        assertEquals(alice, item.getCustomerId());
        assertEquals("田中 太郎", item.getCustomerName(), "姓 + 名 で結合");

        assertEquals(aliceSku, item.getSkuId());
        assertEquals("返品テスト商品", item.getProductName());
        assertEquals("赤", item.getColor());
        assertEquals("M", item.getSize());
    }

    @Test
    void 返品申請が空のときも例外を出さず_List_を返す() {
        // sales_return が空 or 既存件数のみのとき、空配列にせよ既存件数にせよ NPE を出さない
        List<AdminSalesReturnItem> all = service.list();
        assertNotNull(all);
    }

    @Test
    void 申請日時の降順で並ぶ() {
        Long alice = createCustomer("時系列", "テスト");
        Long skuId = createSkuWithStockAndPrice("青", "S", 10, 1000);
        Long firstSalesId = confirmAndDeliver(alice, skuId, 1);

        Long bob = createCustomer("時系列", "テスト2");
        Long bobSku = createSkuWithStockAndPrice("白", "M", 10, 1500);
        Long secondSalesId = confirmAndDeliver(bob, bobSku, 1);

        Long firstReturnId = requestReturn(alice, firstSalesId, 1, "first");
        Long secondReturnId = requestReturn(bob, secondSalesId, 1, "second");

        List<AdminSalesReturnItem> all = service.list();

        // first / second の登場順を確認（後に作った second が先頭側にある）
        int firstIdx = -1, secondIdx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (firstReturnId.equals(all.get(i).getId())) firstIdx = i;
            if (secondReturnId.equals(all.get(i).getId())) secondIdx = i;
        }
        assertTrue(secondIdx >= 0 && firstIdx >= 0);
        assertTrue(secondIdx < firstIdx, "新しい返品申請ほどリストの先頭側に来る");
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

    private Long requestReturn(Long customerId, Long salesId, int quantity, String reason) {
        RequestSalesReturnRequest req = new RequestSalesReturnRequest();
        req.setSalesId(salesId);
        req.setQuantity(quantity);
        req.setReason(reason);
        return requestService.request(customerId, req).getId();
    }

    private Long createCustomer(String last, String first) {
        Customer c = new Customer();
        c.setNameLast(last);
        c.setNameFirst(first);
        c.setPostalCode("1500001");
        c.setAddress("東京都渋谷区神宮前1-1");
        c.setBirthday(LocalDate.of(1995, 5, 5));
        c.setEmail("ls-" + System.nanoTime() + "@example.com");
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
