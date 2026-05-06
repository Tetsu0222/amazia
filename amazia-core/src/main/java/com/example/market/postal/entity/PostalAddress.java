package com.example.market.postal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "postal_addresses", indexes = {
        @Index(name = "idx_pa_postal_code", columnList = "postal_code"),
        @Index(name = "idx_pa_pref_city", columnList = "prefecture, city")
})
public class PostalAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "postal_code", nullable = false, length = 8)
    private String postalCode;

    @Column(nullable = false, length = 20)
    private String prefecture;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 200)
    private String town;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() { updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getPrefecture() { return prefecture; }
    public void setPrefecture(String prefecture) { this.prefecture = prefecture; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getTown() { return town; }
    public void setTown(String town) { this.town = town; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
