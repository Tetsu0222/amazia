package com.example.market.cart.exception;

public class ProductNotPurchasableException extends RuntimeException {
    public ProductNotPurchasableException(String message) {
        super(message);
    }
}
