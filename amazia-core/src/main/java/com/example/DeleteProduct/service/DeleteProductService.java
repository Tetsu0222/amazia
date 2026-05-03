package com.example.DeleteProduct.service;

import com.example.shared.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeleteProductService {

    private final ProductRepository repository;

    public DeleteProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
    }
}
