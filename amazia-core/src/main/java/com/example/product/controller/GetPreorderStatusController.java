package com.example.product.controller;

import com.example.product.dto.PreorderStatusResponse;
import com.example.product.service.PreorderStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GetPreorderStatusController {

    private final PreorderStatusService service;

    public GetPreorderStatusController(PreorderStatusService service) {
        this.service = service;
    }

    @GetMapping("/products/{id}/preorder-status")
    public ResponseEntity<PreorderStatusResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getResponse(id));
    }
}
