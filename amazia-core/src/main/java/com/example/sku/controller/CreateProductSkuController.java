package com.example.sku.controller;

import com.example.sku.entity.ProductSku;
import com.example.sku.service.CreateProductSkuService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CreateProductSkuController {

    private final CreateProductSkuService createProductSkuService;

    public CreateProductSkuController(CreateProductSkuService createProductSkuService) {
        this.createProductSkuService = createProductSkuService;
    }

    @PostMapping("/products/{id}/skus")
    public ResponseEntity<ProductSku> create(@PathVariable Long id, @RequestBody ProductSku request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createProductSkuService.create(id, request));
    }
}
