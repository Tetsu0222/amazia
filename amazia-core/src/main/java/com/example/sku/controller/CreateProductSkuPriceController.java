package com.example.sku.controller;

import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.service.CreateProductSkuPriceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CreateProductSkuPriceController {

    private final CreateProductSkuPriceService createProductSkuPriceService;

    public CreateProductSkuPriceController(CreateProductSkuPriceService createProductSkuPriceService) {
        this.createProductSkuPriceService = createProductSkuPriceService;
    }

    @PostMapping("/skus/{id}/prices")
    public ResponseEntity<ProductSkuPrice> create(@PathVariable Long id, @RequestBody ProductSkuPrice request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createProductSkuPriceService.create(id, request));
    }
}
