package com.example.product.service;

import com.example.product.entity.ProductImage;
import com.example.product.repository.ProductImageRepository;
import com.example.product.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ListProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;

    public ListProductImageService(ProductImageRepository productImageRepository,
                                   ProductRepository productRepository) {
        this.productImageRepository = productImageRepository;
        this.productRepository = productRepository;
    }

    public List<ProductImage> list(Long productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品が見つかりません"));
        return productImageRepository.findByProductIdOrderBySortOrderAsc(productId);
    }
}
