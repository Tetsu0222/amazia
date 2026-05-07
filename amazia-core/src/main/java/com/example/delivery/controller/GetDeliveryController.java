package com.example.delivery.controller;

import com.example.delivery.dto.DeliveryResponse;
import com.example.delivery.service.GetDeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配送詳細 API（GET /api/deliveries/{id}）。
 */
@RestController
@RequestMapping("/api")
public class GetDeliveryController {

    private final GetDeliveryService service;

    public GetDeliveryController(GetDeliveryService service) {
        this.service = service;
    }

    @GetMapping("/deliveries/{id}")
    public ResponseEntity<DeliveryResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }
}
