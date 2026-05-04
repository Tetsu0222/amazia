package com.example.sku.controller;

import com.example.sku.entity.ProductSkuImage;
import com.example.sku.service.CreateProductSkuImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class CreateProductSkuImageController {

    private final CreateProductSkuImageService createProductSkuImageService;

    public CreateProductSkuImageController(CreateProductSkuImageService createProductSkuImageService) {
        this.createProductSkuImageService = createProductSkuImageService;
    }

    @PostMapping("/skus/{id}/images")
    public ResponseEntity<ProductSkuImage> create(
            @PathVariable Long id,
            @RequestParam("image") MultipartFile image) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(createProductSkuImageService.create(id, image));
    }
}
