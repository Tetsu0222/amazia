package com.example.sku.service;

import com.example.sku.entity.ProductSkuStock;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class GetProductSkuStockService {

    private final ProductSkuStockRepository stockRepository;
    private final ProductSkuStockTransactionRepository transactionRepository;
    private final ProductSkuRepository skuRepository;

    public GetProductSkuStockService(ProductSkuStockRepository stockRepository,
                                     ProductSkuStockTransactionRepository transactionRepository,
                                     ProductSkuRepository skuRepository) {
        this.stockRepository = stockRepository;
        this.transactionRepository = transactionRepository;
        this.skuRepository = skuRepository;
    }

    public ProductSkuStock getCurrent(Long skuId) {
        skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKUが見つかりません"));
        return stockRepository.findBySkuId(skuId).orElse(null);
    }

    public List<ProductSkuStockTransaction> getHistory(Long skuId) {
        skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKUが見つかりません"));
        return transactionRepository.findBySkuIdOrderByCreatedAtDesc(skuId);
    }
}
