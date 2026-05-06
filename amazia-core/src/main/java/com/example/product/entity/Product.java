package com.example.product.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    private String description;

    private Integer price;

    private Integer stock;

    private String statusCode;

    private LocalDateTime publishStart;

    private LocalDateTime publishEnd;

    private LocalDate releaseDate;

    private LocalDate preorderStartDate;

    @Column(nullable = false)
    private boolean acceptPreorder;

    @Column(nullable = false)
    private boolean acceptBackorder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (version == null) version = 0L;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isPublished() {
        LocalDateTime now = LocalDateTime.now();
        if (publishStart != null && now.isBefore(publishStart)) return false;
        if (publishEnd   != null && now.isAfter(publishEnd))    return false;
        return true;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public LocalDateTime getPublishStart() { return publishStart; }
    public void setPublishStart(LocalDateTime publishStart) { this.publishStart = publishStart; }
    public LocalDateTime getPublishEnd() { return publishEnd; }
    public void setPublishEnd(LocalDateTime publishEnd) { this.publishEnd = publishEnd; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
    public LocalDate getPreorderStartDate() { return preorderStartDate; }
    public void setPreorderStartDate(LocalDate preorderStartDate) { this.preorderStartDate = preorderStartDate; }
    public boolean isAcceptPreorder() { return acceptPreorder; }
    public void setAcceptPreorder(boolean acceptPreorder) { this.acceptPreorder = acceptPreorder; }
    public boolean isAcceptBackorder() { return acceptBackorder; }
    public void setAcceptBackorder(boolean acceptBackorder) { this.acceptBackorder = acceptBackorder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
