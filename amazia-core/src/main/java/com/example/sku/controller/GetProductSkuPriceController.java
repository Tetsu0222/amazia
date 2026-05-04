package com.example.sku.controller;

import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.service.GetProductSkuPriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GetProductSkuPriceController {

    private final GetProductSkuPriceService getProductSkuPriceService;

    public GetProductSkuPriceController(GetProductSkuPriceService getProductSkuPriceService) {
        this.getProductSkuPriceService = getProductSkuPriceService;
    }

    @GetMapping("/skus/{id}/prices")
    public ResponseEntity<ProductSkuPrice> get(@PathVariable Long id) {
        return ResponseEntity.ok(getProductSkuPriceService.get(id));
    }
}
