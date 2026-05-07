package com.example.shippingmethod.controller;

import com.example.shippingmethod.dto.ShippingMethodResponse;
import com.example.shippingmethod.repository.ShippingMethodRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 配送方法マスタ一覧 API（GET /api/shipping-methods）。
 * マスタ参照のみ。Service を介さず Repository を直接呼ぶ最小実装。
 */
@RestController
@RequestMapping("/api")
public class ListShippingMethodController {

    private final ShippingMethodRepository repository;

    public ListShippingMethodController(ShippingMethodRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/shipping-methods")
    public ResponseEntity<List<ShippingMethodResponse>> list() {
        List<ShippingMethodResponse> items = repository.findAll(Sort.by("id")).stream()
                .map(ShippingMethodResponse::new)
                .toList();
        return ResponseEntity.ok(items);
    }
}
