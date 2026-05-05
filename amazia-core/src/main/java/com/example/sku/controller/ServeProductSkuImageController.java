package com.example.sku.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api")
public class ServeProductSkuImageController {

    @Value("${product.image.storage-path:storage/Product/images}")
    private String storagePath;

    @GetMapping("/skus/{id}/image-file/{filename:.+}")
    public ResponseEntity<Resource> serve(@PathVariable Long id, @PathVariable String filename) {
        Path file = Paths.get(storagePath, "sku", id.toString(), filename);
        Resource resource = new FileSystemResource(file);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }
}
