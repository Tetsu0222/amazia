package com.example.market.cart.service;

import com.example.market.cart.dto.CartItemRequest;
import com.example.market.cart.dto.CartItemResponse;
import com.example.market.cart.dto.CartResponse;
import com.example.market.cart.entity.Cart;
import com.example.market.cart.entity.CartItem;
import com.example.market.cart.exception.InsufficientStockException;
import com.example.market.cart.exception.ProductNotPurchasableException;
import com.example.market.cart.repository.CartItemRepository;
import com.example.market.cart.repository.CartRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * カート操作 Service（フェーズ16.5 §Step 5）。
 *
 * - 1顧客1カート（cart は遅延作成：getOrCreate でアクセス時に INSERT）
 * - 同一 SKU・同一 is_preorder は1行に集約（数量加算）
 * - 在庫超過は InsufficientStockException、SOLD_OUT 等は ProductNotPurchasableException
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductSkuStockRepository skuStockRepository;
    private final ProductSkuPriceRepository skuPriceRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductSkuRepository skuRepository,
                       ProductSkuStockRepository skuStockRepository,
                       ProductSkuPriceRepository skuPriceRepository,
                       ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.skuRepository = skuRepository;
        this.skuStockRepository = skuStockRepository;
        this.skuPriceRepository = skuPriceRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public CartResponse getMyCart(Long customerId) {
        Optional<Cart> cartOpt = cartRepository.findByCustomerId(customerId);
        if (cartOpt.isEmpty()) {
            return new CartResponse(null, List.of(), 0, 0);
        }
        Cart cart = cartOpt.get();
        List<CartItem> items = cartItemRepository.findByCartIdOrderByAddedAtAsc(cart.getId());
        return buildResponse(cart, items);
    }

    @Transactional
    public CartResponse addItem(Long customerId, CartItemRequest req) {
        Cart cart = getOrCreateCart(customerId);
        ProductSku sku = loadSkuForPurchase(req.getSkuId());
        validateStockAvailable(sku, req.getQuantity(), req.isPreorder());

        Optional<CartItem> existing = cartItemRepository
                .findByCartIdAndSkuIdAndPreorder(cart.getId(), sku.getId(), req.isPreorder());

        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQty = item.getQuantity() + req.getQuantity();
            validateStockAvailable(sku, newQty, req.isPreorder());
            item.setQuantity(newQty);
            cartItemRepository.save(item);
        } else {
            CartItem item = new CartItem();
            item.setCartId(cart.getId());
            item.setSkuId(sku.getId());
            item.setQuantity(req.getQuantity());
            item.setPreorder(req.isPreorder());
            cartItemRepository.save(item);
        }

        return getMyCart(customerId);
    }

    @Transactional
    public CartResponse updateItemQuantity(Long customerId, Long itemId, int quantity) {
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be positive");
        }
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cart not found"));
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cart item not found"));
        if (!item.getCartId().equals(cart.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "cart item not found");
        }
        ProductSku sku = loadSkuForPurchase(item.getSkuId());
        validateStockAvailable(sku, quantity, item.isPreorder());
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return getMyCart(customerId);
    }

    @Transactional
    public CartResponse removeItem(Long customerId, Long itemId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cart not found"));
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cart item not found"));
        if (!item.getCartId().equals(cart.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "cart item not found");
        }
        cartItemRepository.delete(item);
        return getMyCart(customerId);
    }

    @Transactional
    public void clearCart(Long customerId) {
        cartRepository.findByCustomerId(customerId).ifPresent(cart -> {
            cartItemRepository.deleteByCartId(cart.getId());
        });
    }

    private Cart getOrCreateCart(Long customerId) {
        return cartRepository.findByCustomerId(customerId).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setCustomerId(customerId);
            return cartRepository.save(cart);
        });
    }

    private ProductSku loadSkuForPurchase(Long skuId) {
        ProductSku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sku not found"));
        if (!"ACTIVE".equals(sku.getStatus())) {
            throw new ProductNotPurchasableException("sku is not active");
        }
        return sku;
    }

    private void validateStockAvailable(ProductSku sku, int requestedQuantity, boolean preorder) {
        if (preorder) return;
        ProductSkuStock stock = skuStockRepository.findBySkuId(sku.getId())
                .orElseThrow(() -> new ProductNotPurchasableException("sku stock not registered"));
        if (stock.getQuantity() < requestedQuantity) {
            throw new InsufficientStockException(
                    "requested quantity " + requestedQuantity + " exceeds available stock " + stock.getQuantity());
        }
    }

    private CartResponse buildResponse(Cart cart, List<CartItem> items) {
        List<CartItemResponse> itemResponses = new ArrayList<>(items.size());
        int totalPrice = 0;
        int totalCount = 0;

        for (CartItem item : items) {
            ProductSku sku = skuRepository.findById(item.getSkuId()).orElse(null);
            if (sku == null) continue;
            Product product = productRepository.findById(sku.getProductId()).orElse(null);
            if (product == null) continue;

            int unitPrice = skuPriceRepository.findBySkuId(sku.getId())
                    .map(ProductSkuPrice::getPrice).orElse(0);
            int subtotal = unitPrice * item.getQuantity();
            int availableStock = skuStockRepository.findBySkuId(sku.getId())
                    .map(ProductSkuStock::getQuantity).orElse(0);

            CartItemResponse r = new CartItemResponse();
            r.setItemId(item.getId());
            r.setSkuId(sku.getId());
            r.setProductId(product.getId());
            r.setProductName(product.getName());
            r.setColor(sku.getColor());
            r.setSize(sku.getSize());
            r.setUnitPrice(unitPrice);
            r.setQuantity(item.getQuantity());
            r.setSubtotal(subtotal);
            r.setAvailableStock(availableStock);
            r.setPreorder(item.isPreorder());
            itemResponses.add(r);

            totalPrice += subtotal;
            totalCount += item.getQuantity();
        }
        return new CartResponse(cart.getId(), itemResponses, totalCount, totalPrice);
    }
}
