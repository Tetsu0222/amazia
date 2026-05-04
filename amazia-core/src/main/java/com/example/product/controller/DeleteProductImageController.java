package com.example.product.controller;

import com.example.product.service.DeleteProductImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DeleteProductImageController {

    private final DeleteProductImageService deleteProductImageService;

    public DeleteProductImageController(DeleteProductImageService deleteProductImageService) {
        this.deleteProductImageService = deleteProductImageService;
    }

    @DeleteMapping("/product-images/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteProductImageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
