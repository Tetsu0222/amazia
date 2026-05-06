package com.example.product.service;

import com.example.product.dto.PreorderStatusResponse;
import com.example.product.entity.PreorderStatus;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class PreorderStatusService {

    private final ProductRepository productRepository;
    private final ProductSkuStockRepository stockRepository;
    private final Clock clock;

    public PreorderStatusService(
            ProductRepository productRepository,
            ProductSkuStockRepository stockRepository,
            Clock clock) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.clock = clock;
    }

    public PreorderStatus judge(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return judgeInternal(product);
    }

    public PreorderStatusResponse getResponse(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return PreorderStatusResponse.of(product, judgeInternal(product));
    }

    private PreorderStatus judgeInternal(Product product) {
        LocalDate today = LocalDate.now(clock);

        if (product.getPublishStart() != null
                && today.isBefore(product.getPublishStart().toLocalDate())) {
            return PreorderStatus.NOT_PUBLIC;
        }

        if (product.getPreorderStartDate() != null
                && today.isBefore(product.getPreorderStartDate())) {
            return PreorderStatus.PRE_ORDER_NOT_STARTED;
        }

        if (product.getReleaseDate() != null
                && today.isBefore(product.getReleaseDate())) {
            return PreorderStatus.PRE_ORDER;
        }

        long totalStock = stockRepository.sumQuantityByProductId(product.getId());
        if (totalStock > 0) {
            return PreorderStatus.ON_SALE;
        }
        if (product.isAcceptBackorder()) {
            return PreorderStatus.BACK_ORDER;
        }
        return PreorderStatus.SOLD_OUT;
    }
}
