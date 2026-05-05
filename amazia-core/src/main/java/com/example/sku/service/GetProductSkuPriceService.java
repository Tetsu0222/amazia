package com.example.sku.service;

import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GetProductSkuPriceService {

    private final ProductSkuPriceRepository priceRepository;
    private final ProductSkuRepository skuRepository;

    public GetProductSkuPriceService(ProductSkuPriceRepository priceRepository,
                                     ProductSkuRepository skuRepository) {
        this.priceRepository = priceRepository;
        this.skuRepository = skuRepository;
    }

    public ProductSkuPrice get(Long skuId) {
        skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKUが見つかりません"));
        return priceRepository.findBySkuId(skuId).orElse(null);
    }
}
