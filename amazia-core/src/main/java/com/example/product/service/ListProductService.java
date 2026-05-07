package com.example.product.service;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ListProductService {

    private final ProductRepository repository;
    private final PreorderStatusService preorderStatusService;

    public ListProductService(ProductRepository repository,
                              PreorderStatusService preorderStatusService) {
        this.repository = repository;
        this.preorderStatusService = preorderStatusService;
    }

    public List<Product> getPublished() {
        // 設計書 phase14_5_preorder_status.md §2-2: 公開判定は JST 0:00 基準。
        // 旧 Product#isPublished()（秒単位）は廃止し PreorderStatusService に集約。
        return repository.findAll().stream()
                .filter(preorderStatusService::isPublished)
                .collect(Collectors.toList());
    }

    public List<Product> getAll() {
        return repository.findAll();
    }
}
