package com.example.sku.controller;

import com.example.sku.entity.ProductSku;
import com.example.sku.service.GetProductSkuByCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GetProductSkuByCodeController {

    private final GetProductSkuByCodeService getProductSkuByCodeService;

    public GetProductSkuByCodeController(GetProductSkuByCodeService getProductSkuByCodeService) {
        this.getProductSkuByCodeService = getProductSkuByCodeService;
    }

    @GetMapping("/skus/by-code/{code}")
    public ResponseEntity<ProductSku> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(getProductSkuByCodeService.getByCode(code));
    }
}
