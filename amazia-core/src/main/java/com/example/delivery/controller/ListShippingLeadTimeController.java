package com.example.delivery.controller;

import com.example.delivery.dto.ShippingLeadTimeResponse;
import com.example.delivery.service.ListShippingLeadTimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 都道府県別リードタイム一覧 API（GET /api/shipping-lead-times[?shippingMethodId=N]）。
 */
@RestController
@RequestMapping("/api")
public class ListShippingLeadTimeController {

    private final ListShippingLeadTimeService service;

    public ListShippingLeadTimeController(ListShippingLeadTimeService service) {
        this.service = service;
    }

    @GetMapping("/shipping-lead-times")
    public ResponseEntity<List<ShippingLeadTimeResponse>> list(
            @RequestParam(required = false) Long shippingMethodId) {
        return ResponseEntity.ok(service.list(shippingMethodId));
    }
}
