package com.example.GetProduct.controller;

import com.example.GetProduct.service.GetProductService;
import com.example.shared.entity.Product;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class GetProductController {

    private final GetProductService getProductService;

    public GetProductController(GetProductService getProductService) {
        this.getProductService = getProductService;
    }

    @GetMapping("/products")
    public List<Product> getPublished() {
        return getProductService.getPublished();
    }

    @GetMapping("/admin/products")
    public List<Product> getAll() {
        return getProductService.getAll();
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return ResponseEntity.ok(getProductService.getById(id));
    }
}
