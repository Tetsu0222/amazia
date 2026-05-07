package com.example.market.cart.dto;

import java.util.List;

public class CartResponse {

    private Long cartId;
    private List<CartItemResponse> items;
    private Integer totalCount;
    private Integer totalPrice;

    public CartResponse() {}

    public CartResponse(Long cartId, List<CartItemResponse> items, Integer totalCount, Integer totalPrice) {
        this.cartId = cartId;
        this.items = items;
        this.totalCount = totalCount;
        this.totalPrice = totalPrice;
    }

    public Long getCartId() { return cartId; }
    public void setCartId(Long cartId) { this.cartId = cartId; }
    public List<CartItemResponse> getItems() { return items; }
    public void setItems(List<CartItemResponse> items) { this.items = items; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getTotalPrice() { return totalPrice; }
    public void setTotalPrice(Integer totalPrice) { this.totalPrice = totalPrice; }
}
