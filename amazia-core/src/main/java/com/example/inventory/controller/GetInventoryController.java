package com.example.inventory.controller;

import com.example.inventory.service.GetInventoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class GetInventoryController {

    private final GetInventoryService getInventoryService;

    public GetInventoryController(GetInventoryService getInventoryService) {
        this.getInventoryService = getInventoryService;
    }

    @GetMapping
    public List<Map<String, Object>> getInventory() {
        return getInventoryService.getInventory();
    }
}
