package com.example.product.controller;

import com.example.product.entity.BulkStockRequest;
import com.example.product.entity.Product;
import com.example.product.service.BulkUpdateStockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BulkUpdateStockController {

    private final BulkUpdateStockService bulkUpdateStockService;

    public BulkUpdateStockController(BulkUpdateStockService bulkUpdateStockService) {
        this.bulkUpdateStockService = bulkUpdateStockService;
    }

    @PatchMapping("/products/bulk-stock")
    public ResponseEntity<List<Product>> bulkUpdateStock(@RequestBody List<BulkStockRequest> requests) {
        return ResponseEntity.ok(bulkUpdateStockService.bulkUpdateStock(requests));
    }
}
