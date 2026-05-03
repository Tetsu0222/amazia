package com.example;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class ProductController {

    private final ProductRepository repository;
    private final ProductStatusRepository statusRepository;

    private static final Set<String> VALID_STATUS_CODES = Set.of("WAITING", "RESERVATION", "ON_SALE");

    public ProductController(ProductRepository repository, ProductStatusRepository statusRepository) {
        this.repository = repository;
        this.statusRepository = statusRepository;
    }

    /** Market向け：公開期間内の商品のみ返す */
    @GetMapping("/api/products")
    public List<Product> getPublished() {
        return repository.findAll().stream()
                .filter(Product::isPublished)
                .collect(Collectors.toList());
    }

    /** Console向け：全件返す（公開期間外も含む） */
    @GetMapping("/api/admin/products")
    public List<Product> getAll() {
        return repository.findAll();
    }

    @GetMapping("/api/products/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/products")
    public ResponseEntity<Product> create(@Valid @RequestBody Product product) {
        if (product.getStatusCode() != null && !VALID_STATUS_CODES.contains(product.getStatusCode())) {
            return ResponseEntity.badRequest().build();
        }
        Product saved = repository.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/api/products/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @Valid @RequestBody Product request) {
        if (request.getStatusCode() != null && !VALID_STATUS_CODES.contains(request.getStatusCode())) {
            return ResponseEntity.badRequest().build();
        }
        return repository.findById(id).map(existing -> {
            existing.setName(request.getName());
            existing.setDescription(request.getDescription());
            existing.setPrice(request.getPrice());
            existing.setStock(request.getStock());
            existing.setStatusCode(request.getStatusCode());
            existing.setPublishStart(request.getPublishStart());
            existing.setPublishEnd(request.getPublishEnd());
            return ResponseEntity.ok(repository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/products/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/products")
    public ResponseEntity<Void> bulkDelete(@RequestParam String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
        repository.deleteAllById(idList);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/products/bulk-stock")
    public ResponseEntity<List<Product>> bulkUpdateStock(@RequestBody List<BulkStockRequest> requests) {
        List<Product> updated = requests.stream()
                .filter(r -> repository.existsById(r.getId()))
                .map(r -> {
                    Product p = repository.findById(r.getId()).get();
                    p.setStock(r.getStock());
                    return repository.save(p);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/api/product-statuses")
    public List<ProductStatus> getStatuses() {
        return statusRepository.findAll();
    }
}
