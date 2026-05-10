package com.example.delivery.controller;

import com.example.delivery.dto.ShippingLeadTimeResponse;
import com.example.delivery.dto.UpdateShippingLeadTimeRequest;
import com.example.delivery.service.UpdateShippingLeadTimeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UpdateShippingLeadTimeController {

    private final UpdateShippingLeadTimeService service;

    public UpdateShippingLeadTimeController(UpdateShippingLeadTimeService service) {
        this.service = service;
    }

    @PatchMapping("/shipping-lead-times/{id}")
    public ResponseEntity<ShippingLeadTimeResponse> update(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateShippingLeadTimeRequest request,
                                                           @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(service.update(id, request.getLeadTimeDays(), userId));
    }
}
