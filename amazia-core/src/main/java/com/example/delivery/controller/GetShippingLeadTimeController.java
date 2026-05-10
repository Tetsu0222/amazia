package com.example.delivery.controller;

import com.example.delivery.dto.ShippingLeadTimeResponse;
import com.example.delivery.service.GetShippingLeadTimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GetShippingLeadTimeController {

    private final GetShippingLeadTimeService service;

    public GetShippingLeadTimeController(GetShippingLeadTimeService service) {
        this.service = service;
    }

    @GetMapping("/shipping-lead-times/{id}")
    public ResponseEntity<ShippingLeadTimeResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }
}
