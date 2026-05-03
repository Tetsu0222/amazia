package com.example.inventory;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class InventoryService {

    public List<Map<String, Object>> getInventory() {
        return List.of(
            Map.of("id", 1, "name", "商品A", "quantity", 100),
            Map.of("id", 2, "name", "商品B", "quantity", 50),
            Map.of("id", 3, "name", "商品C", "quantity", 200)
        );
    }
}
