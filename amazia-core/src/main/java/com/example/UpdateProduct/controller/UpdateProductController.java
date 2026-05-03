package com.example.UpdateProduct.controller;

import com.example.UpdateProduct.service.UpdateProductService;
import com.example.shared.entity.Product;
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
