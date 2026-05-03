package com.example.product.service;

import com.example.product.entity.ProductStatus;
import com.example.product.repository.ProductStatusRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetProductStatusService {

    private final ProductStatusRepository repository;

    public GetProductStatusService(ProductStatusRepository repository) {
        this.repository = repository;
    }

    public List<ProductStatus> getStatuses() {
        return repository.findAll();
    }
}
