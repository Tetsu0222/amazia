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

    /**
     * 設計書 phase14_5_preorder_status.md §2-2 の JST 0:00 基準で公開期間内かを判定する。
     * 既存の Product#isPublished() (秒単位 LocalDateTime) を置き換える統一判定窓口。
     * publishStart / publishEnd が NULL のときは「制限なし」扱い（phase14 既存挙動）。
     * フェーズ16 Step1: is_active = FALSE は期間に関わらず非公開扱い（Market 露出 OFF）。
     */
    public boolean isPublished(Product product) {
        if (!product.isActive()) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        if (product.getPublishStart() != null
                && today.isBefore(product.getPublishStart().toLocalDate())) {
            return false;
        }
        if (product.getPublishEnd() != null
                && today.isAfter(product.getPublishEnd().toLocalDate())) {
            return false;
        }
        return true;
    }

    private PreorderStatus judgeInternal(Product product) {
        LocalDate today = LocalDate.now(clock);

        if (!product.isActive()) {
            return PreorderStatus.NOT_PUBLIC;
        }

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
