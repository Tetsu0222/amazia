package com.example.notice.service;

import com.example.notice.dto.NoticeCategoryDto;
import com.example.notice.repository.NoticeCategoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * お知らせ分類マスタ取得（GET /api/notice-categories）。
 * 認証不要・全件返却（display_order 昇順）。
 */
@Service
public class GetNoticeCategoriesService {

    private final NoticeCategoryRepository repository;

    public GetNoticeCategoriesService(NoticeCategoryRepository repository) {
        this.repository = repository;
    }

    public List<NoticeCategoryDto> findAll() {
        return repository.findAll(Sort.by("displayOrder").ascending()).stream()
                .map(NoticeCategoryDto::from)
                .toList();
    }
}
