package com.example.product.service;

import com.example.product.entity.ProductImage;
import com.example.product.repository.ProductImageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class DeleteProductImageService {

    private final ProductImageRepository productImageRepository;

    @Value("${product.image.storage-path:storage/Product/images}")
    private String storagePath;

    public DeleteProductImageService(ProductImageRepository productImageRepository) {
        this.productImageRepository = productImageRepository;
    }

    public void delete(Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "画像が見つかりません"));

        try {
            Files.deleteIfExists(Paths.get(storagePath, image.getImagePath()));
        } catch (IOException e) {
            // ファイルが既に存在しない場合は無視してDB削除を続行
        }

        productImageRepository.deleteById(imageId);
    }
}
