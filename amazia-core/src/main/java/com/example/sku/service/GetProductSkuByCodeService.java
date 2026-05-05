package com.example.sku.service;

import com.example.sku.entity.ProductSku;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GetProductSkuByCodeService {

    private final ProductSkuRepository skuRepository;

    public GetProductSkuByCodeService(ProductSkuRepository skuRepository) {
        this.skuRepository = skuRepository;
    }

    public ProductSku getByCode(String skuCode) {
        return skuRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "SKUが見つかりません: " + skuCode));
    }
}
