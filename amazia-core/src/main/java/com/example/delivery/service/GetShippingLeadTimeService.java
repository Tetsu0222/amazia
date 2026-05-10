package com.example.delivery.service;

import com.example.delivery.dto.ShippingLeadTimeResponse;
import com.example.delivery.repository.ShippingLeadTimeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GetShippingLeadTimeService {

    private final ShippingLeadTimeRepository repository;

    public GetShippingLeadTimeService(ShippingLeadTimeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ShippingLeadTimeResponse get(Long id) {
        return repository.findById(id)
                .map(ShippingLeadTimeResponse::new)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "shipping_lead_time not found"));
    }
}
