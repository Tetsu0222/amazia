package com.example.BulkUpdateStock.controller;

import com.example.BulkUpdateStock.entity.BulkStockRequest;
import com.example.BulkUpdateStock.service.BulkUpdateStockService;
import com.example.shared.entity.Product;
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
