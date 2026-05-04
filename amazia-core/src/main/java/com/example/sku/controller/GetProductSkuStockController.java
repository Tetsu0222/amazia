package com.example.sku.controller;

import com.example.sku.entity.ProductSkuStock;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.service.GetProductSkuStockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class GetProductSkuStockController {

    private final GetProductSkuStockService getProductSkuStockService;

    public GetProductSkuStockController(GetProductSkuStockService getProductSkuStockService) {
        this.getProductSkuStockService = getProductSkuStockService;
    }

    @GetMapping("/skus/{id}/stocks")
    public ResponseEntity<ProductSkuStock> getCurrent(@PathVariable Long id) {
        return ResponseEntity.ok(getProductSkuStockService.getCurrent(id));
    }

    @GetMapping("/skus/{id}/stocks/history")
    public ResponseEntity<List<ProductSkuStockTransaction>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(getProductSkuStockService.getHistory(id));
    }
}
