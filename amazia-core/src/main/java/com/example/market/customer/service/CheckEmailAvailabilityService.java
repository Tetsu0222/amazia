package com.example.market.customer.service;

import com.example.market.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckEmailAvailabilityService {

    private final CustomerRepository customerRepository;

    public CheckEmailAvailabilityService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(String email) {
        return !customerRepository.existsByEmail(email);
    }
}
