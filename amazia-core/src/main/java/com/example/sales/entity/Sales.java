package com.example.sales.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales")
public class Sales {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "payment_method_id", nullable = false)
    private Long paymentMethodId;

    @Column(name = "shipping_method_id", nullable = false)
    private Long shippingMethodId;

    @Column(name = "shipping_address_id", nullable = false)
    private Long shippingAddressId;

    @Column(name = "shipping_status_id", nullable = false)
    private Long shippingStatusId;

    @Column(name = "payment_id", nullable = false, unique = true, length = 100)
    private String paymentId;

    @Column(name = "is_preorder", nullable = false)
    private boolean isPreorder = false;

    @Column(name = "sales_date", nullable = false)
    private LocalDate salesDate;

    @Column(name = "shipping_date")
    private LocalDate shippingDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public Long getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(Long paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    public Long getShippingMethodId() { return shippingMethodId; }
    public void setShippingMethodId(Long shippingMethodId) { this.shippingMethodId = shippingMethodId; }
    public Long getShippingAddressId() { return shippingAddressId; }
    public void setShippingAddressId(Long shippingAddressId) { this.shippingAddressId = shippingAddressId; }
    public Long getShippingStatusId() { return shippingStatusId; }
    public void setShippingStatusId(Long shippingStatusId) { this.shippingStatusId = shippingStatusId; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public boolean isPreorder() { return isPreorder; }
    public void setPreorder(boolean preorder) { isPreorder = preorder; }
    public LocalDate getSalesDate() { return salesDate; }
    public void setSalesDate(LocalDate salesDate) { this.salesDate = salesDate; }
    public LocalDate getShippingDate() { return shippingDate; }
    public void setShippingDate(LocalDate shippingDate) { this.shippingDate = shippingDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
