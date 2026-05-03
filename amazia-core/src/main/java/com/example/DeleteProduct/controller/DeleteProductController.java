package com.example.DeleteProduct.controller;

import com.example.DeleteProduct.service.DeleteProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DeleteProductController {

    private final DeleteProductService deleteProductService;

    public DeleteProductController(DeleteProductService deleteProductService) {
        this.deleteProductService = deleteProductService;
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteProductService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
