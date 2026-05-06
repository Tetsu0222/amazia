package com.example.market.customer.controller;

import com.example.market.customer.entity.MarketSession;
import com.example.market.customer.filter.MarketSessionAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/customer")
public class CsrfTokenController {

    @GetMapping("/csrf-token")
    public ResponseEntity<Map<String, String>> token(HttpServletRequest request) {
        MarketSession session = (MarketSession) request.getAttribute(MarketSessionAuthFilter.ATTR_SESSION);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        return ResponseEntity.ok(Map.of("csrfToken", session.getCsrfToken()));
    }
}
