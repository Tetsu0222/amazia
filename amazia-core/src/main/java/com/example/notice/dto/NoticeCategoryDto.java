package com.example.notice.dto;

import com.example.notice.entity.NoticeCategory;

/**
 * お知らせ分類 DTO（Market / Console 共通）。
 */
public record NoticeCategoryDto(Long id, String code, String label, Integer displayOrder) {

    public static NoticeCategoryDto from(NoticeCategory entity) {
        return new NoticeCategoryDto(
                entity.getId(),
                entity.getCode(),
                entity.getLabel(),
                entity.getDisplayOrder()
        );
    }
}
