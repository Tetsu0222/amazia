package com.example.inquiry.service;

import com.example.inquiry.dto.InquiryListFilter;
import com.example.inquiry.dto.InquiryListResponse;
import com.example.inquiry.entity.Inquiry;
import com.example.inquiry.repository.InquiryRepository;
import com.example.market.customer.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 問い合わせ一覧取得（Console / Market 両用 / フェーズ18）。
 *
 * <p>Console は全件にフィルタを掛けて取得、Market は自分の問い合わせのみを強制（IDOR 対策）。
 * 表示用 {@code userName} は phase13 {@code Customer.nameLast} + ' ' + {@code nameFirst}（RV-4）。
 */
@Service
public class ListInquiryService {

    private final InquiryRepository inquiryRepository;
    private final CustomerRepository customerRepository;
    private final InquiryTargetLabelResolver targetLabelResolver;

    public ListInquiryService(InquiryRepository inquiryRepository,
                              CustomerRepository customerRepository,
                              InquiryTargetLabelResolver targetLabelResolver) {
        this.inquiryRepository = inquiryRepository;
        this.customerRepository = customerRepository;
        this.targetLabelResolver = targetLabelResolver;
    }

    @Transactional(readOnly = true)
    public Page<InquiryListResponse> listForConsole(InquiryListFilter filter, Pageable pageable) {
        Page<Inquiry> page = inquiryRepository.searchForConsole(
                filter.status(), filter.targetType(),
                filter.dateFrom(), filter.dateTo(),
                filter.userNameLike(), pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<InquiryListResponse> listForMarket(Long marketCustomerId, Pageable pageable) {
        Page<Inquiry> page = inquiryRepository.findByUserIdOrderByUpdatedAtDesc(marketCustomerId, pageable);
        return page.map(this::toResponse);
    }

    private InquiryListResponse toResponse(Inquiry i) {
        String userName = resolveUserName(i.getUserId());
        String targetLabel = targetLabelResolver.resolve(i.getTargetType(), i.getTargetId());
        return new InquiryListResponse(
                i.getId(), i.getUserId(), userName, i.getSubject(), i.getStatus(),
                i.getTargetType(), i.getTargetId(), targetLabel,
                i.getCreatedAt(), i.getUpdatedAt());
    }

    private String resolveUserName(Long customerId) {
        return customerRepository.findById(customerId)
                .map(c -> c.getNameLast() + " " + c.getNameFirst())
                .orElse("（不明な顧客）");
    }
}
