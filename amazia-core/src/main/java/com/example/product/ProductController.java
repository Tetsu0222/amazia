package com.example.product;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public List<Product> getPublished() {
        return productService.getPublished();
    }

    @GetMapping("/admin/products")
    public List<Product> getAll() {
        return productService.getAll();
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @PostMapping("/products")
    public ResponseEntity<Product> create(@Valid @RequestBody Product product) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(product));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @Valid @RequestBody Product request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/products")
    public ResponseEntity<Void> bulkDelete(@RequestParam String ids) {
        productService.bulkDelete(ids);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/products/bulk-stock")
    public ResponseEntity<List<Product>> bulkUpdateStock(@RequestBody List<BulkStockRequest> requests) {
        return ResponseEntity.ok(productService.bulkUpdateStock(requests));
    }

    @GetMapping("/product-statuses")
    public List<ProductStatus> getStatuses() {
        return productService.getStatuses();
    }
}
