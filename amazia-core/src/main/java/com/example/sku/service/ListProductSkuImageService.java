package com.example.sku.service;

import com.example.sku.entity.ProductSkuImage;
import com.example.sku.repository.ProductSkuImageRepository;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ListProductSkuImageService {

    private final ProductSkuImageRepository imageRepository;
    private final ProductSkuRepository skuRepository;

    public ListProductSkuImageService(ProductSkuImageRepository imageRepository,
                                      ProductSkuRepository skuRepository) {
        this.imageRepository = imageRepository;
        this.skuRepository = skuRepository;
    }

    public List<ProductSkuImage> list(Long skuId) {
        skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKUが見つかりません"));
        return imageRepository.findBySkuIdOrderBySortOrderAsc(skuId);
    }
}
