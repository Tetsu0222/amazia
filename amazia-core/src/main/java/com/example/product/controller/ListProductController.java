package com.example.product.controller;

import com.example.product.entity.Product;
import com.example.product.service.ListProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ListProductController {

    private final ListProductService listProductService;

    public ListProductController(ListProductService listProductService) {
        this.listProductService = listProductService;
    }

    @GetMapping("/products")
    public List<Product> getPublished() {
        return listProductService.getPublished();
    }

    @GetMapping("/admin/products")
    public List<Product> getAll() {
        return listProductService.getAll();
    }
}
