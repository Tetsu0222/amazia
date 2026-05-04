package com.example.product.controller;

import com.example.product.entity.ProductImage;
import com.example.product.service.ListProductImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ListProductImageController {

    private final ListProductImageService listProductImageService;

    public ListProductImageController(ListProductImageService listProductImageService) {
        this.listProductImageService = listProductImageService;
    }

    @GetMapping("/products/{id}/images")
    public ResponseEntity<List<ProductImage>> list(@PathVariable Long id) {
        return ResponseEntity.ok(listProductImageService.list(id));
    }
}
