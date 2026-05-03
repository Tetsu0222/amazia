package com.example.product.service;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ListProductService {

    private final ProductRepository repository;

    public ListProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> getPublished() {
        return repository.findAll().stream()
                .filter(Product::isPublished)
                .collect(Collectors.toList());
    }

    public List<Product> getAll() {
        return repository.findAll();
    }
}
