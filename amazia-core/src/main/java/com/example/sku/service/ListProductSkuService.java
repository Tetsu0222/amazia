package com.example.sku.service;

import com.example.product.repository.ProductRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ListProductSkuService {

    private final ProductSkuRepository skuRepository;
    private final ProductRepository productRepository;

    public ListProductSkuService(ProductSkuRepository skuRepository,
                                 ProductRepository productRepository) {
        this.skuRepository = skuRepository;
        this.productRepository = productRepository;
    }

    public List<ProductSku> list(Long productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品が見つかりません"));
        return skuRepository.findByProductId(productId);
    }
}
