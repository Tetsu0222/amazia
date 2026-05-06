package com.example.market.postal.dto;

import com.example.market.postal.entity.PostalAddress;

public class PostalAddressResponse {

    private final String postalCode;
    private final String prefecture;
    private final String city;
    private final String town;

    public PostalAddressResponse(String postalCode, String prefecture, String city, String town) {
        this.postalCode = postalCode;
        this.prefecture = prefecture;
        this.city = city;
        this.town = town;
    }

    public static PostalAddressResponse from(PostalAddress entity) {
        return new PostalAddressResponse(
                entity.getPostalCode(),
                entity.getPrefecture(),
                entity.getCity(),
                entity.getTown()
        );
    }

    public String getPostalCode() { return postalCode; }
    public String getPrefecture() { return prefecture; }
    public String getCity() { return city; }
    public String getTown() { return town; }
}
