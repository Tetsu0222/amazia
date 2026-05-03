package com.example.BulkDeleteProduct.service;

import com.example.shared.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BulkDeleteProductService {

    private final ProductRepository repository;

    public BulkDeleteProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public void bulkDelete(String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
        repository.deleteAllById(idList);
    }
}
