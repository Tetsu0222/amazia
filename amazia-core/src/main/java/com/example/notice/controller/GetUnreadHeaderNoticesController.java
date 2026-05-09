package com.example.notice.controller;

import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.notice.dto.NoticeMarketDto;
import com.example.notice.service.GetUnreadHeaderNoticesService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * ヘッダー表示用未読お知らせ取得（GET /api/customer/notices/unread）。会員セッション必須。
 */
@RestController
@RequestMapping("/api/customer/notices")
public class GetUnreadHeaderNoticesController {

    private final GetUnreadHeaderNoticesService service;

    public GetUnreadHeaderNoticesController(GetUnreadHeaderNoticesService service) {
        this.service = service;
    }

    @GetMapping("/unread")
    public List<NoticeMarketDto> getUnread(HttpServletRequest request) {
        Long customerId = (Long) request.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        return service.findUnread(customerId);
    }
}
