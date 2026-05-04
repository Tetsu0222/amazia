package com.example.product.service;

import com.example.product.entity.ProductImage;
import com.example.product.repository.ProductImageRepository;
import com.example.product.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class CreateProductImageService {

    private static final long MAX_BYTES = 200 * 1024L;

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;

    @Value("${product.image.storage-path:storage/Product/images}")
    private String storagePath;

    public CreateProductImageService(ProductImageRepository productImageRepository,
                                     ProductRepository productRepository) {
        this.productImageRepository = productImageRepository;
        this.productRepository = productRepository;
    }

    public ProductImage create(Long productId, MultipartFile file) throws IOException {
        productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品が見つかりません"));

        validateFile(file);

        String savedPath = saveFile(productId, file);

        ProductImage image = new ProductImage();
        image.setProductId(productId);
        image.setImagePath(savedPath);
        int nextOrder = productImageRepository.findMaxSortOrderByProductId(productId) + 1;
        image.setSortOrder(nextOrder);

        return productImageRepository.save(image);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ファイルが選択されていません");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ファイルサイズは200KB以下にしてください");
        }
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        if (!"image/png".equals(contentType)
                || originalFilename == null
                || !originalFilename.toLowerCase().endsWith(".png")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PNG形式のファイルのみ登録できます");
        }
    }

    private String saveFile(Long productId, MultipartFile file) throws IOException {
        String uuid = UUID.randomUUID().toString();
        Path dir = Paths.get(storagePath, productId.toString());
        Files.createDirectories(dir);
        Path target = dir.resolve(uuid + ".png");
        file.transferTo(target);
        return productId + "/" + uuid + ".png";
    }
}
