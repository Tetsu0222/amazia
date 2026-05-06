package com.example.market.customer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class RegisterCustomerRequest {

    @NotBlank
    @Size(max = 100)
    private String nameLast;

    @NotBlank
    @Size(max = 100)
    private String nameFirst;

    @NotBlank
    @Pattern(regexp = "\\d{7}", message = "postal code must be 7 digits")
    private String postalCode;

    @NotBlank
    @Size(max = 255)
    private String address;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String passwordConfirm;

    @NotBlank
    @Pattern(regexp = "credit_card|bank_transfer|other")
    private String paymentMethod;

    private String cardToken;

    public String getNameLast() { return nameLast; }
    public void setNameLast(String nameLast) { this.nameLast = nameLast; }
    public String getNameFirst() { return nameFirst; }
    public void setNameFirst(String nameFirst) { this.nameFirst = nameFirst; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPasswordConfirm() { return passwordConfirm; }
    public void setPasswordConfirm(String passwordConfirm) { this.passwordConfirm = passwordConfirm; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getCardToken() { return cardToken; }
    public void setCardToken(String cardToken) { this.cardToken = cardToken; }
}
