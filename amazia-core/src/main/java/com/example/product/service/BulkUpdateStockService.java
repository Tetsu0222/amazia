package com.example.product.service;

import com.example.product.entity.BulkStockRequest;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BulkUpdateStockService {

    private final ProductRepository repository;

    public BulkUpdateStockService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> bulkUpdateStock(List<BulkStockRequest> requests) {
        return requests.stream()
                .filter(r -> repository.existsById(r.getId()))
                .map(r -> {
                    Product p = repository.findById(r.getId()).get();
                    p.setStock(r.getStock());
                    return repository.save(p);
                })
                .collect(Collectors.toList());
    }
}
