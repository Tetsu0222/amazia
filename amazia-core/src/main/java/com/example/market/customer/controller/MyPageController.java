package com.example.market.customer.controller;

import com.example.market.customer.dto.CustomerResponse;
import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.market.customer.service.GetMyPageCustomerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer")
public class MyPageController {

    private final GetMyPageCustomerService service;

    public MyPageController(GetMyPageCustomerService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerResponse> me(HttpServletRequest request) {
        Long customerId = (Long) request.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        return ResponseEntity.ok(new CustomerResponse(service.get(customerId)));
    }
}
