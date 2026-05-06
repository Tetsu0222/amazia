package com.example.market.customer.dto;

import com.example.market.customer.entity.Customer;

import java.time.LocalDate;

public class CustomerResponse {

    private final Long id;
    private final String nameLast;
    private final String nameFirst;
    private final String postalCode;
    private final String address;
    private final LocalDate birthday;
    private final String email;
    private final String paymentMethod;

    public CustomerResponse(Customer c) {
        this.id = c.getId();
        this.nameLast = c.getNameLast();
        this.nameFirst = c.getNameFirst();
        this.postalCode = c.getPostalCode();
        this.address = c.getAddress();
        this.birthday = c.getBirthday();
        this.email = c.getEmail();
        this.paymentMethod = c.getPaymentMethod();
    }

    public Long getId() { return id; }
    public String getNameLast() { return nameLast; }
    public String getNameFirst() { return nameFirst; }
    public String getPostalCode() { return postalCode; }
    public String getAddress() { return address; }
    public LocalDate getBirthday() { return birthday; }
    public String getEmail() { return email; }
    public String getPaymentMethod() { return paymentMethod; }
}
