package com.example.product.controller;

import com.example.product.entity.Product;
import com.example.product.service.UpdateProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UpdateProductController {

    private final UpdateProductService updateProductService;

    public UpdateProductController(UpdateProductService updateProductService) {
        this.updateProductService = updateProductService;
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @Valid @RequestBody Product request) {
        return ResponseEntity.ok(updateProductService.update(id, request));
    }
}
