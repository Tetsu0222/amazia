package com.example.delivery.controller;

import com.example.delivery.dto.DeliveryResponse;
import com.example.delivery.service.ListDeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 配送一覧 API（GET /api/deliveries[?shippingStatusId=N]）。
 */
@RestController
@RequestMapping("/api")
public class ListDeliveryController {

    private final ListDeliveryService service;

    public ListDeliveryController(ListDeliveryService service) {
        this.service = service;
    }

    @GetMapping("/deliveries")
    public ResponseEntity<List<DeliveryResponse>> list(
            @RequestParam(required = false) Long shippingStatusId) {
        return ResponseEntity.ok(service.list(shippingStatusId));
    }
}
