package com.example.GetProduct.service;

import com.example.shared.entity.Product;
import com.example.shared.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GetProductService {

    private final ProductRepository repository;

    public GetProductService(ProductRepository repository) {
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

    public Product getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
