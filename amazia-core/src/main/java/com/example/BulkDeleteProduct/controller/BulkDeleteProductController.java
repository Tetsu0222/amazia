package com.example.BulkDeleteProduct.controller;

import com.example.BulkDeleteProduct.service.BulkDeleteProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BulkDeleteProductController {

    private final BulkDeleteProductService bulkDeleteProductService;

    public BulkDeleteProductController(BulkDeleteProductService bulkDeleteProductService) {
        this.bulkDeleteProductService = bulkDeleteProductService;
    }

    @DeleteMapping("/products")
    public ResponseEntity<Void> bulkDelete(@RequestParam String ids) {
        bulkDeleteProductService.bulkDelete(ids);
        return ResponseEntity.noContent().build();
    }
}
