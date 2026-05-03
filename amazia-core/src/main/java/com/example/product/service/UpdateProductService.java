package com.example.product.service;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.product.validator.ProductStatusValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UpdateProductService {

    private final ProductRepository repository;
    private final ProductStatusValidator statusValidator;

    public UpdateProductService(ProductRepository repository, ProductStatusValidator statusValidator) {
        this.repository = repository;
        this.statusValidator = statusValidator;
    }

    public Product update(Long id, Product request) {
        statusValidator.validate(request.getStatusCode());
        Product existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setPrice(request.getPrice());
        existing.setStock(request.getStock());
        existing.setStatusCode(request.getStatusCode());
        existing.setPublishStart(request.getPublishStart());
        existing.setPublishEnd(request.getPublishEnd());
        return repository.save(existing);
    }
}
