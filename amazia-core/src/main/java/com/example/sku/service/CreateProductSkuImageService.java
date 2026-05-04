package com.example.sku.service;

import com.example.sku.entity.ProductSkuImage;
import com.example.sku.repository.ProductSkuImageRepository;
import com.example.sku.repository.ProductSkuRepository;
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
public class CreateProductSkuImageService {

    private static final long MAX_BYTES = 200 * 1024L;

    private final ProductSkuImageRepository imageRepository;
    private final ProductSkuRepository skuRepository;

    @Value("${product.image.storage-path:storage/Product/images}")
    private String storagePath;

    public CreateProductSkuImageService(ProductSkuImageRepository imageRepository,
                                        ProductSkuRepository skuRepository) {
        this.imageRepository = imageRepository;
        this.skuRepository = skuRepository;
    }

    public ProductSkuImage create(Long skuId, MultipartFile file) throws IOException {
        skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKUが見つかりません"));

        validateFile(file);

        String savedPath = saveFile(skuId, file);

        ProductSkuImage image = new ProductSkuImage();
        image.setSkuId(skuId);
        image.setImagePath(savedPath);
        int nextOrder = imageRepository.findMaxSortOrderBySkuId(skuId) + 1;
        image.setSortOrder(nextOrder);

        return imageRepository.save(image);
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

    private String saveFile(Long skuId, MultipartFile file) throws IOException {
        String uuid = UUID.randomUUID().toString();
        Path dir = Paths.get(storagePath, "sku", skuId.toString());
        Files.createDirectories(dir);
        Path target = dir.resolve(uuid + ".png");
        file.transferTo(target);
        return "sku/" + skuId + "/" + uuid + ".png";
    }
}
