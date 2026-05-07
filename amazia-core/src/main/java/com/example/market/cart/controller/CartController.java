package com.example.market.cart.controller;

import com.example.market.cart.dto.CartItemRequest;
import com.example.market.cart.dto.CartResponse;
import com.example.market.cart.dto.UpdateCartItemRequest;
import com.example.market.cart.exception.InsufficientStockException;
import com.example.market.cart.exception.ProductNotPurchasableException;
import com.example.market.cart.service.CartService;
import com.example.market.customer.filter.MarketSessionAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * カート API（フェーズ16.5 §Step 5）。
 *
 * 認証: Cookie ベース MarketSession（MarketSessionAuthFilter で customerId を取得）
 * CSRF: /api/customer/ 配下のため MarketCsrfFilter で X-CSRF-Token 検証あり
 */
@RestController
@RequestMapping("/api/customer/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/me")
    public ResponseEntity<CartResponse> getMyCart(HttpServletRequest req) {
        return ResponseEntity.ok(cartService.getMyCart(requireCustomerId(req)));
    }

    @PostMapping("/me/items")
    public ResponseEntity<CartResponse> addItem(@Valid @RequestBody CartItemRequest body,
                                                HttpServletRequest req) {
        return ResponseEntity.ok(cartService.addItem(requireCustomerId(req), body));
    }

    @PutMapping("/me/items/{itemId}")
    public ResponseEntity<CartResponse> updateItem(@PathVariable Long itemId,
                                                   @Valid @RequestBody UpdateCartItemRequest body,
                                                   HttpServletRequest req) {
        return ResponseEntity.ok(cartService.updateItemQuantity(requireCustomerId(req), itemId, body.getQuantity()));
    }

    @DeleteMapping("/me/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(@PathVariable Long itemId, HttpServletRequest req) {
        return ResponseEntity.ok(cartService.removeItem(requireCustomerId(req), itemId));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> clearCart(HttpServletRequest req) {
        cartService.clearCart(requireCustomerId(req));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<String> handleInsufficientStock(InsufficientStockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(ProductNotPurchasableException.class)
    public ResponseEntity<String> handleNotPurchasable(ProductNotPurchasableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    private Long requireCustomerId(HttpServletRequest req) {
        Long customerId = (Long) req.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        return customerId;
    }
}
