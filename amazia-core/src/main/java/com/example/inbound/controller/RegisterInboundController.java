package com.example.inbound.controller;

import com.example.inbound.dto.InboundResponse;
import com.example.inbound.dto.RegisterInboundRequest;
import com.example.inbound.service.RegisterInboundService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 入荷登録 API（POST /api/inbounds）。
 *
 * <p>操作者の users.id は X-User-Id ヘッダで受け取る（既存ワークフロー API と同じ作法）。
 * リクエストには warehouse_id を含めない（RRRR-5：UI に表示せずバックエンドが DEFAULT=1 を自動セット）。
 */
@RestController
@RequestMapping("/api")
public class RegisterInboundController {

    private final RegisterInboundService service;

    public RegisterInboundController(RegisterInboundService service) {
        this.service = service;
    }

    @PostMapping("/inbounds")
    public ResponseEntity<InboundResponse> register(@Valid @RequestBody RegisterInboundRequest request,
                                                    @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new InboundResponse(service.register(request, userId)));
    }
}
