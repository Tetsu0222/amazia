package com.example.sku.service;

import com.example.sku.entity.ProductSkuStock;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReceiveProductSkuStockService {

    private final ProductSkuStockRepository stockRepository;
    private final ProductSkuStockTransactionRepository transactionRepository;
    private final ProductSkuRepository skuRepository;

    public ReceiveProductSkuStockService(ProductSkuStockRepository stockRepository,
                                         ProductSkuStockTransactionRepository transactionRepository,
                                         ProductSkuRepository skuRepository) {
        this.stockRepository = stockRepository;
        this.transactionRepository = transactionRepository;
        this.skuRepository = skuRepository;
    }

    @Transactional
    public ProductSkuStock receive(Long skuId, Integer quantity) {
        skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKUが見つかりません"));

        ProductSkuStock stock = stockRepository.findBySkuId(skuId)
                .orElseGet(() -> {
                    ProductSkuStock s = new ProductSkuStock();
                    s.setSkuId(skuId);
                    s.setQuantity(0);
                    return s;
                });

        stock.setQuantity(stock.getQuantity() + quantity);
        stockRepository.save(stock);

        ProductSkuStockTransaction tx = new ProductSkuStockTransaction();
        tx.setSkuId(skuId);
        tx.setType("receive");
        tx.setQuantity(quantity);
        transactionRepository.save(tx);

        return stock;
    }
}
