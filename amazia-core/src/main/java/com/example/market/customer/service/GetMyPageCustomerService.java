package com.example.market.customer.service;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GetMyPageCustomerService {

    private final CustomerRepository customerRepository;

    public GetMyPageCustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Customer get(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found"));
    }
}
