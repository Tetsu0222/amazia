package com.example.sku.controller;

import com.example.sku.entity.ProductSku;
import com.example.sku.service.ListProductSkuService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ListProductSkuController {

    private final ListProductSkuService listProductSkuService;

    public ListProductSkuController(ListProductSkuService listProductSkuService) {
        this.listProductSkuService = listProductSkuService;
    }

    @GetMapping("/products/{id}/skus")
    public ResponseEntity<List<ProductSku>> list(@PathVariable Long id) {
        return ResponseEntity.ok(listProductSkuService.list(id));
    }
}
