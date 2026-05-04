package com.example.product.controller;

import com.example.product.entity.ProductImage;
import com.example.product.service.UpdateProductImageSortService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class UpdateProductImageSortController {

    private final UpdateProductImageSortService updateProductImageSortService;

    public UpdateProductImageSortController(UpdateProductImageSortService updateProductImageSortService) {
        this.updateProductImageSortService = updateProductImageSortService;
    }

    @PutMapping("/product-images/{id}/sort")
    public ResponseEntity<ProductImage> updateSort(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        return ResponseEntity.ok(updateProductImageSortService.updateSort(id, body.get("sortOrder")));
    }
}
