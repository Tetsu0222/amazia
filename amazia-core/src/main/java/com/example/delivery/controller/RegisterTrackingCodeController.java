package com.example.delivery.controller;

import com.example.delivery.dto.DeliveryResponse;
import com.example.delivery.dto.RegisterTrackingCodeRequest;
import com.example.delivery.entity.Delivery;
import com.example.delivery.service.RegisterTrackingCodeService;
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
public class RegisterTrackingCodeController {

    private final RegisterTrackingCodeService service;

    public RegisterTrackingCodeController(RegisterTrackingCodeService service) {
        this.service = service;
    }

    @PatchMapping("/deliveries/{id}/tracking-code")
    public ResponseEntity<DeliveryResponse> register(@PathVariable Long id,
                                                     @Valid @RequestBody RegisterTrackingCodeRequest request,
                                                     @RequestHeader("X-User-Id") Long userId) {
        Delivery updated = service.register(id, request.getTrackingCode(), userId);
        return ResponseEntity.ok(new DeliveryResponse(updated));
    }
}
