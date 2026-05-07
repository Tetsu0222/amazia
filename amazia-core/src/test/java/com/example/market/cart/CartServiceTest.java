package com.example.market.cart;

import com.example.market.cart.dto.CartItemRequest;
import com.example.market.cart.dto.CartResponse;
import com.example.market.cart.exception.InsufficientStockException;
import com.example.market.cart.exception.ProductNotPurchasableException;
import com.example.market.cart.repository.CartItemRepository;
import com.example.market.cart.repository.CartRepository;
import com.example.market.cart.service.CartService;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ16.5 §Step 5：カート機能の Service レイヤーテスト。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class CartServiceTest {

    @Autowired private CartService cartService;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private ProductSkuStockRepository skuStockRepository;
    @Autowired private ProductSkuPriceRepository skuPriceRepository;

    private Long customerId;
    private Long productId;
    private Long skuId;

    @BeforeEach
    void setUp() {
        customerId = createCustomer();
        productId = createProduct();
        skuId = createSku(productId, 10, 3000);
    }

    @Test
    void 空のカートは空配列と合計0を返す() {
        CartResponse res = cartService.getMyCart(customerId);
        assertNotNull(res);
        assertTrue(res.getItems().isEmpty());
        assertEquals(0, res.getTotalCount());
        assertEquals(0, res.getTotalPrice());
    }

    @Test
    void SKU追加でカートが作成され_合計と小計が計算される() {
        CartResponse res = cartService.addItem(customerId, buildAdd(skuId, 2, false));

        assertNotNull(res.getCartId());
        assertEquals(1, res.getItems().size());
        var item = res.getItems().get(0);
        assertEquals(skuId, item.getSkuId());
        assertEquals(2, item.getQuantity());
        assertEquals(3000, item.getUnitPrice());
        assertEquals(6000, item.getSubtotal());
        assertEquals(2, res.getTotalCount());
        assertEquals(6000, res.getTotalPrice());
    }

    @Test
    void 同一SKUを2回追加すると数量が加算され行は1つ() {
        cartService.addItem(customerId, buildAdd(skuId, 2, false));
        CartResponse res = cartService.addItem(customerId, buildAdd(skuId, 3, false));

        assertEquals(1, res.getItems().size());
        assertEquals(5, res.getItems().get(0).getQuantity());
        assertEquals(15000, res.getTotalPrice());
    }

    @Test
    void preorderフラグが異なれば別行になる() {
        cartService.addItem(customerId, buildAdd(skuId, 1, false));
        CartResponse res = cartService.addItem(customerId, buildAdd(skuId, 2, true));

        assertEquals(2, res.getItems().size());
        assertEquals(3, res.getTotalCount());
    }

    @Test
    void 在庫超過の追加はInsufficientStockExceptionをスローする() {
        assertThrows(InsufficientStockException.class,
                () -> cartService.addItem(customerId, buildAdd(skuId, 11, false)));
    }

    @Test
    void 既存数量と合算して在庫超過になる追加もスローする() {
        cartService.addItem(customerId, buildAdd(skuId, 8, false));
        assertThrows(InsufficientStockException.class,
                () -> cartService.addItem(customerId, buildAdd(skuId, 5, false)));
    }

    @Test
    void 非ACTIVE_SKUの追加はProductNotPurchasableExceptionをスローする() {
        ProductSku sku = skuRepository.findById(skuId).orElseThrow();
        sku.setStatus("INACTIVE");
        skuRepository.save(sku);

        assertThrows(ProductNotPurchasableException.class,
                () -> cartService.addItem(customerId, buildAdd(skuId, 1, false)));
    }

    @Test
    void 予約購入は在庫不足でも追加できる() {
        CartResponse res = cartService.addItem(customerId, buildAdd(skuId, 100, true));
        assertEquals(1, res.getItems().size());
        assertEquals(100, res.getItems().get(0).getQuantity());
    }

    @Test
    void 数量変更で行が更新される() {
        CartResponse added = cartService.addItem(customerId, buildAdd(skuId, 2, false));
        Long itemId = added.getItems().get(0).getItemId();

        CartResponse res = cartService.updateItemQuantity(customerId, itemId, 5);
        assertEquals(5, res.getItems().get(0).getQuantity());
    }

    @Test
    void 数量変更で在庫超過は拒否される() {
        CartResponse added = cartService.addItem(customerId, buildAdd(skuId, 2, false));
        Long itemId = added.getItems().get(0).getItemId();

        assertThrows(InsufficientStockException.class,
                () -> cartService.updateItemQuantity(customerId, itemId, 11));
    }

    @Test
    void 数量0以下の更新は400をスローする() {
        CartResponse added = cartService.addItem(customerId, buildAdd(skuId, 2, false));
        Long itemId = added.getItems().get(0).getItemId();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cartService.updateItemQuantity(customerId, itemId, 0));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void 削除した行はカートから消える() {
        CartResponse added = cartService.addItem(customerId, buildAdd(skuId, 2, false));
        Long itemId = added.getItems().get(0).getItemId();

        CartResponse res = cartService.removeItem(customerId, itemId);
        assertTrue(res.getItems().isEmpty());
    }

    @Test
    void 他人のカートのitemIdへの操作は404() {
        Long otherCustomerId = createCustomer();
        CartResponse mine = cartService.addItem(customerId, buildAdd(skuId, 1, false));
        Long itemId = mine.getItems().get(0).getItemId();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cartService.removeItem(otherCustomerId, itemId));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void clearCartで全アイテム削除されカートは残る() {
        cartService.addItem(customerId, buildAdd(skuId, 2, false));
        cartService.clearCart(customerId);

        CartResponse res = cartService.getMyCart(customerId);
        assertTrue(res.getItems().isEmpty());
        assertNotNull(res.getCartId(), "clearCart はカート行は残す（next 追加時に再利用）");
    }

    // ---- helpers --------------------------------------------------------

    private CartItemRequest buildAdd(Long skuId, int quantity, boolean preorder) {
        CartItemRequest req = new CartItemRequest();
        req.setSkuId(skuId);
        req.setQuantity(quantity);
        req.setPreorder(preorder);
        return req;
    }

    private Long createCustomer() {
        Customer c = new Customer();
        c.setNameLast("山田");
        c.setNameFirst("太郎");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("buyer-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummy");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        return customerRepository.save(c).getId();
    }

    private Long createProduct() {
        Product p = new Product();
        p.setName("テスト商品");
        p.setDescription("テスト");
        p.setPrice(3000);
        p.setStock(0);
        p.setStatusCode("ON_SALE");
        p.setPublishStart(LocalDateTime.now().minusDays(1));
        p.setPublishEnd(LocalDateTime.now().plusYears(1));
        return productRepository.save(p).getId();
    }

    private Long createSku(Long productId, int quantity, int price) {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("SKU-" + System.nanoTime());
        sku.setColor("赤");
        sku.setSize("M");
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
