package com.example.sku.controller;

import com.example.sku.dto.ProductMarketDetail;
import com.example.sku.dto.ProductMarketSummary;
import com.example.sku.service.ListProductMarketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ListProductMarketController {

    private final ListProductMarketService listProductMarketService;

    public ListProductMarketController(ListProductMarketService listProductMarketService) {
        this.listProductMarketService = listProductMarketService;
    }

    @GetMapping("/products/market")
    public ResponseEntity<List<ProductMarketSummary>> listMarket() {
        return ResponseEntity.ok(listProductMarketService.listMarket());
    }

    @GetMapping("/products/{id}/market")
    public ResponseEntity<ProductMarketDetail> getMarketDetail(@PathVariable Long id) {
        return ResponseEntity.ok(listProductMarketService.getMarketDetail(id));
    }
}
