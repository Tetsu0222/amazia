package com.example.salesreport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "yearly_sales_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ysr_axes",
                columnNames = {"`year`", "product_id", "payment_method_id", "shipping_method_id", "is_preorder"}))
public class YearlySalesReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "`year`", nullable = false)
    private Short year;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "payment_method_id")
    private Long paymentMethodId;

    @Column(name = "shipping_method_id")
    private Long shippingMethodId;

    @Column(name = "is_preorder")
    private Boolean isPreorder;

    @NotNull
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @NotNull
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Short getYear() { return year; }
    public void setYear(Short year) { this.year = year; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(Long paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    public Long getShippingMethodId() { return shippingMethodId; }
    public void setShippingMethodId(Long shippingMethodId) { this.shippingMethodId = shippingMethodId; }
    public Boolean getIsPreorder() { return isPreorder; }
    public void setIsPreorder(Boolean isPreorder) { this.isPreorder = isPreorder; }
    public Long getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Long totalAmount) { this.totalAmount = totalAmount; }
    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
