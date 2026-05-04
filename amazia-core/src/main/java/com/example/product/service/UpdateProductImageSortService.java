package com.example.product.service;

import com.example.product.entity.ProductImage;
import com.example.product.repository.ProductImageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UpdateProductImageSortService {

    private final ProductImageRepository productImageRepository;

    public UpdateProductImageSortService(ProductImageRepository productImageRepository) {
        this.productImageRepository = productImageRepository;
    }

    public ProductImage updateSort(Long imageId, Integer sortOrder) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "画像が見つかりません"));
        image.setSortOrder(sortOrder);
        return productImageRepository.save(image);
    }
}
