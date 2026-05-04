package com.example.sku.controller;

import com.example.sku.entity.ProductSkuStock;
import com.example.sku.service.ReceiveProductSkuStockService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReceiveProductSkuStockController {

    private final ReceiveProductSkuStockService receiveProductSkuStockService;

    public ReceiveProductSkuStockController(ReceiveProductSkuStockService receiveProductSkuStockService) {
        this.receiveProductSkuStockService = receiveProductSkuStockService;
    }

    @PostMapping("/skus/{id}/stocks/receive")
    public ResponseEntity<ProductSkuStock> receive(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(receiveProductSkuStockService.receive(id, body.get("quantity")));
    }
}
