package com.example.product.controller;

import com.example.product.entity.ProductStatus;
import com.example.product.service.GetProductStatusService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class GetProductStatusController {

    private final GetProductStatusService getProductStatusService;

    public GetProductStatusController(GetProductStatusService getProductStatusService) {
        this.getProductStatusService = getProductStatusService;
    }

    @GetMapping("/product-statuses")
    public List<ProductStatus> getStatuses() {
        return getProductStatusService.getStatuses();
    }
}
