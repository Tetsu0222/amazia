package com.example.product;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final Set<String> VALID_STATUS_CODES = Set.of("WAITING", "RESERVATION", "ON_SALE");

    private final ProductRepository repository;
    private final ProductStatusRepository statusRepository;

    public ProductService(ProductRepository repository, ProductStatusRepository statusRepository) {
        this.repository = repository;
        this.statusRepository = statusRepository;
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

    public Product create(Product product) {
        validateStatusCode(product.getStatusCode());
        return repository.save(product);
    }

    public Product update(Long id, Product request) {
        validateStatusCode(request.getStatusCode());
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

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
    }

    public void bulkDelete(String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
        repository.deleteAllById(idList);
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

    public List<ProductStatus> getStatuses() {
        return statusRepository.findAll();
    }

    private void validateStatusCode(String statusCode) {
        if (statusCode != null && !VALID_STATUS_CODES.contains(statusCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status code: " + statusCode);
        }
    }
}
