package com.example.inbound.controller;

import com.example.inbound.dto.InboundResponse;
import com.example.inbound.service.ListInboundService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 入荷一覧 API（GET /api/inbounds[?productId=N]）。
 */
@RestController
@RequestMapping("/api")
public class ListInboundController {

    private final ListInboundService service;

    public ListInboundController(ListInboundService service) {
        this.service = service;
    }

    @GetMapping("/inbounds")
    public ResponseEntity<List<InboundResponse>> list(
            @RequestParam(required = false) Long productId) {
        return ResponseEntity.ok(service.list(productId));
    }
}
