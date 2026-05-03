package com.example.CreateProduct.controller;

import com.example.CreateProduct.service.CreateProductService;
import com.example.shared.entity.Product;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CreateProductController {

    private final CreateProductService createProductService;

    public CreateProductController(CreateProductService createProductService) {
        this.createProductService = createProductService;
    }

    @PostMapping("/products")
    public ResponseEntity<Product> create(@Valid @RequestBody Product product) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createProductService.create(product));
    }
}
