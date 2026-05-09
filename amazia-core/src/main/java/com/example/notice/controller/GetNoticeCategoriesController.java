package com.example.notice.controller;

import com.example.notice.dto.NoticeCategoryDto;
import com.example.notice.service.GetNoticeCategoriesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * お知らせ分類マスタ取得（GET /api/notice-categories）。認証不要。
 */
@RestController
@RequestMapping("/api/notice-categories")
public class GetNoticeCategoriesController {

    private final GetNoticeCategoriesService service;

    public GetNoticeCategoriesController(GetNoticeCategoriesService service) {
        this.service = service;
    }

    @GetMapping
    public List<NoticeCategoryDto> getAll() {
        return service.findAll();
    }
}
