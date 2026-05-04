package com.example.product.controller;

import com.example.product.entity.ProductImage;
import com.example.product.service.CreateProductImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class CreateProductImageController {

    private final CreateProductImageService createProductImageService;

    public CreateProductImageController(CreateProductImageService createProductImageService) {
        this.createProductImageService = createProductImageService;
    }

    @PostMapping("/products/{id}/images")
    public ResponseEntity<ProductImage> create(
            @PathVariable Long id,
            @RequestParam("image") MultipartFile image) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(createProductImageService.create(id, image));
    }
}
