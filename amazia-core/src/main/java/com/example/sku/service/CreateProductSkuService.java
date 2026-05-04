package com.example.sku.service;

import com.example.product.repository.ProductRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CreateProductSkuService {

    private final ProductSkuRepository skuRepository;
    private final ProductRepository productRepository;

    public CreateProductSkuService(ProductSkuRepository skuRepository,
                                   ProductRepository productRepository) {
        this.skuRepository = skuRepository;
        this.productRepository = productRepository;
    }

    public ProductSku create(Long productId, ProductSku request) {
        productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品が見つかりません"));

        if (skuRepository.existsByProductIdAndColorAndSize(productId, request.getColor(), request.getSize())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同じ色・サイズのSKUが既に存在します");
        }

        request.setProductId(productId);
        request.setSkuCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return skuRepository.save(request);
    }
}
