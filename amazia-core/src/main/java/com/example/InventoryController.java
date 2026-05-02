package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @GetMapping
    public List<Map<String, Object>> getInventory() {
        return List.of(
            Map.of("id", 1, "name", "商品A", "quantity", 100),
            Map.of("id", 2, "name", "商品B", "quantity", 50),
            Map.of("id", 3, "name", "商品C", "quantity", 200)
        );
    }
}
