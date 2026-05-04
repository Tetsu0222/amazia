package com.example.sku.controller;

import com.example.sku.entity.ProductSkuImage;
import com.example.sku.service.ListProductSkuImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ListProductSkuImageController {

    private final ListProductSkuImageService listProductSkuImageService;

    public ListProductSkuImageController(ListProductSkuImageService listProductSkuImageService) {
        this.listProductSkuImageService = listProductSkuImageService;
    }

    @GetMapping("/skus/{id}/images")
    public ResponseEntity<List<ProductSkuImage>> list(@PathVariable Long id) {
        return ResponseEntity.ok(listProductSkuImageService.list(id));
    }
}
