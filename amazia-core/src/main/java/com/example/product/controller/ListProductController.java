package com.example.product.controller;

import com.example.product.dto.ProductAdminSummary;
import com.example.product.entity.Product;
import com.example.product.service.AdminListProductService;
import com.example.product.service.ListProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ListProductController {

    private final ListProductService listProductService;
    private final AdminListProductService adminListProductService;

    public ListProductController(ListProductService listProductService,
                                 AdminListProductService adminListProductService) {
        this.listProductService = listProductService;
        this.adminListProductService = adminListProductService;
    }

    @GetMapping("/products")
    public List<Product> getPublished() {
        return listProductService.getPublished();
    }

    @GetMapping("/admin/products")
    public List<ProductAdminSummary> getAll() {
        return adminListProductService.getAll();
    }
}
