package com.example.product.controller;

import com.example.product.entity.Product;
import com.example.product.service.GetProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GetProductController {

    private final GetProductService getProductService;

    public GetProductController(GetProductService getProductService) {
        this.getProductService = getProductService;
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return ResponseEntity.ok(getProductService.getById(id));
    }
}
