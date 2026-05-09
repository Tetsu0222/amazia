package com.example.notice.controller;

import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.notice.dto.UnreadCountResponse;
import com.example.notice.service.GetUnreadCountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Market 会員向け未読数集計（GET /api/customer/notices/unread-count）。会員セッション必須。
 */
@RestController
@RequestMapping("/api/customer/notices")
public class GetUnreadCountController {

    private final GetUnreadCountService service;

    public GetUnreadCountController(GetUnreadCountService service) {
        this.service = service;
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse getCount(HttpServletRequest request) {
        Long customerId = (Long) request.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        return service.count(customerId);
    }
}
