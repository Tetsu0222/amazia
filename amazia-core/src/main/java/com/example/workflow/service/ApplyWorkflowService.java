package com.example.workflow.service;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.workflow.config.WorkflowStepDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ワークフロー反映処理。target_type に応じて該当エンティティを更新する。
 * 反映時には @Version 楽観ロック + payload.before == 現値 の二重検証を行う。
 */
@Service
public class ApplyWorkflowService {

    private final ProductRepository productRepository;
    private final ProductSkuPriceRepository priceRepository;
    private final ProductSkuStockRepository stockRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApplyWorkflowService(ProductRepository productRepository,
                                ProductSkuPriceRepository priceRepository,
                                ProductSkuStockRepository stockRepository) {
        this.productRepository = productRepository;
        this.priceRepository   = priceRepository;
        this.stockRepository   = stockRepository;
    }

    @Transactional
    public void apply(String targetType, Long targetId, String payloadJson) {
        Map<String, Object> payload = parse(payloadJson);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) payload.get("fields");
        if (fields == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "payload.fields is required");
        }

        try {
            switch (targetType) {
                case WorkflowStepDefinition.TARGET_PRODUCT -> applyProduct(targetId, fields);
                case WorkflowStepDefinition.TARGET_PRICE   -> applyPrice(targetId, fields);
                case WorkflowStepDefinition.TARGET_STOCK   -> applyStock(targetId, fields);
                default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unknown target_type: " + targetType);
            }
        } catch (OptimisticLockException | OptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Target was modified concurrently (version mismatch)");
        }
    }

    private void applyProduct(Long productId, List<Map<String, Object>> fields) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        for (Map<String, Object> f : fields) {
            String fieldName = (String) f.get("field");
            Object before    = f.get("before");
            Object after     = f.get("after");

            switch (fieldName) {
                case "statusCode" -> {
                    verifyBefore(fieldName, before, product.getStatusCode());
                    product.setStatusCode(toStr(after));
                }
                case "name" -> {
                    verifyBefore(fieldName, before, product.getName());
                    product.setName(toStr(after));
                }
                case "description" -> {
                    verifyBefore(fieldName, before, product.getDescription());
                    product.setDescription(toStr(after));
                }
                default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unsupported field for product: " + fieldName);
            }
        }
        productRepository.save(product);
    }

    private void applyPrice(Long skuId, List<Map<String, Object>> fields) {
        ProductSkuPrice price = priceRepository.findBySkuId(skuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Price not found"));

        for (Map<String, Object> f : fields) {
            String fieldName = (String) f.get("field");
            Object before    = f.get("before");
            Object after     = f.get("after");

            if ("price".equals(fieldName)) {
                verifyBefore(fieldName, before, price.getPrice());
                price.setPrice(toInt(after));
            } else {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unsupported field for price: " + fieldName);
            }
        }
        priceRepository.save(price);
    }

    private void applyStock(Long skuId, List<Map<String, Object>> fields) {
        ProductSkuStock stock = stockRepository.findBySkuId(skuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found"));

        for (Map<String, Object> f : fields) {
            String fieldName = (String) f.get("field");
            Object before    = f.get("before");
            Object after     = f.get("after");

            if ("quantity".equals(fieldName)) {
                verifyBefore(fieldName, before, stock.getQuantity());
                stock.setQuantity(toInt(after));
            } else {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unsupported field for stock: " + fieldName);
            }
        }
        stockRepository.save(stock);
    }

    private void verifyBefore(String fieldName, Object before, Object current) {
        // before が未指定（null）の場合は新規設定として許容する
        if (before == null && current == null) return;

        Object normalizedBefore = normalize(before);
        Object normalizedCurrent = normalize(current);

        if (!Objects.equals(normalizedBefore, normalizedCurrent)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Field '" + fieldName + "' has drifted: expected before=" + before
                    + ", actual=" + current);
        }
    }

    private Object normalize(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return v.toString();
    }

    private String toStr(Object v) {
        return v == null ? null : v.toString();
    }

    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Invalid payload JSON");
        }
    }
}
