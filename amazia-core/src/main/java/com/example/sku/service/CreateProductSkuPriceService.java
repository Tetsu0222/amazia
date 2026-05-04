package com.example.sku.service;

import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreateProductSkuPriceService {

    private final ProductSkuPriceRepository priceRepository;
    private final ProductSkuRepository skuRepository;

    public CreateProductSkuPriceService(ProductSkuPriceRepository priceRepository,
                                        ProductSkuRepository skuRepository) {
        this.priceRepository = priceRepository;
        this.skuRepository = skuRepository;
    }

    public ProductSkuPrice create(Long skuId, ProductSkuPrice request) {
        skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKUが見つかりません"));

        priceRepository.findBySkuId(skuId).ifPresent(existing -> priceRepository.delete(existing));

        request.setSkuId(skuId);
        return priceRepository.save(request);
    }
}
