package com.example.GetProductStatus.controller;

import com.example.GetProductStatus.service.GetProductStatusService;
import com.example.shared.entity.ProductStatus;
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
