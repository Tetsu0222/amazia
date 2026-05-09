package com.example.inquiry.service;

import com.example.inquiry.repository.InquiryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Console ベルマーク用の未対応件数取得（フェーズ18 / 設計書 §6.3）。
 *
 * <p>真実の元は {@code inquiries.status='NEW'} の COUNT。`console_notifications` ではない（設計書 §6.3）。
 */
@Service
public class GetUnreadInquiryCountService {

    private static final String STATUS_NEW = "NEW";

    private final InquiryRepository inquiryRepository;

    public GetUnreadInquiryCountService(InquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    @Transactional(readOnly = true)
    public long count() {
        return inquiryRepository.countByStatus(STATUS_NEW);
    }
}
