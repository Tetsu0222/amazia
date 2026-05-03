package com.example.CreateProduct.service;

import com.example.shared.entity.Product;
import com.example.shared.repository.ProductRepository;
import com.example.shared.validator.ProductStatusValidator;
import org.springframework.stereotype.Service;

@Service
public class CreateProductService {

    private final ProductRepository repository;
    private final ProductStatusValidator statusValidator;

    public CreateProductService(ProductRepository repository, ProductStatusValidator statusValidator) {
        this.repository = repository;
        this.statusValidator = statusValidator;
    }

    public Product create(Product product) {
        statusValidator.validate(product.getStatusCode());
        return repository.save(product);
    }
}
