package com.example.inquiry.service;

import com.example.auth.repository.UserRepository;
import com.example.inquiry.dto.InquiryDetailResponse;
import com.example.inquiry.dto.InquiryMessageResponse;
import com.example.inquiry.entity.Inquiry;
import com.example.inquiry.entity.InquiryMessage;
import com.example.inquiry.exception.ForbiddenInquiryAccessException;
import com.example.inquiry.exception.InquiryNotFoundException;
import com.example.inquiry.repository.InquiryMessageRepository;
import com.example.inquiry.repository.InquiryRepository;
import com.example.market.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 問い合わせ詳細取得（フェーズ18）。
 *
 * <p>Console 詳細はメッセージを内部メモ含めて全件返す。Market 詳細は内部メモ除外。
 * Market 側は自身の問い合わせ以外を取得しようとすると {@link ForbiddenInquiryAccessException}（403）。
 */
@Service
public class GetInquiryService {

    private static final String SENDER_TYPE_CUSTOMER = "market_customer";
    private static final String SENDER_TYPE_ADMIN    = "admin_user";

    private final InquiryRepository inquiryRepository;
    private final InquiryMessageRepository messageRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final InquiryTargetLabelResolver targetLabelResolver;

    public GetInquiryService(InquiryRepository inquiryRepository,
                             InquiryMessageRepository messageRepository,
                             CustomerRepository customerRepository,
                             UserRepository userRepository,
                             InquiryTargetLabelResolver targetLabelResolver) {
        this.inquiryRepository = inquiryRepository;
        this.messageRepository = messageRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.targetLabelResolver = targetLabelResolver;
    }

    @Transactional(readOnly = true)
    public InquiryDetailResponse getForConsole(Long inquiryId) {
        Inquiry inquiry = requireInquiry(inquiryId);
        List<InquiryMessage> messages =
                messageRepository.findByInquiryIdOrderByCreatedAtAsc(inquiryId);
        return toResponse(inquiry, messages);
    }

    @Transactional(readOnly = true)
    public InquiryDetailResponse getForMarket(Long inquiryId, Long marketCustomerId) {
        Inquiry inquiry = requireInquiry(inquiryId);
        if (!Objects.equals(inquiry.getUserId(), marketCustomerId)) {
            throw new ForbiddenInquiryAccessException(
                    "inquiry does not belong to current customer");
        }
        List<InquiryMessage> messages =
                messageRepository.findByInquiryIdAndIsInternalNoteFalseOrderByCreatedAtAsc(inquiryId);
        return toResponse(inquiry, messages);
    }

    private Inquiry requireInquiry(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new InquiryNotFoundException("inquiry not found: " + inquiryId));
    }

    private InquiryDetailResponse toResponse(Inquiry i, List<InquiryMessage> messages) {
        String userName = resolveCustomerName(i.getUserId());
        String targetLabel = targetLabelResolver.resolve(i.getTargetType(), i.getTargetId());
        Map<String, String> senderNameCache = new HashMap<>();
        List<InquiryMessageResponse> dtoMessages = messages.stream()
                .map(m -> new InquiryMessageResponse(
                        m.getId(), m.getSenderType(), m.getSenderId(),
                        resolveSenderName(m, senderNameCache),
                        m.getMessage(),
                        Boolean.TRUE.equals(m.getIsInternalNote()),
                        m.getCreatedAt()))
                .toList();
        return new InquiryDetailResponse(
                i.getId(), i.getUserId(), userName, i.getSubject(), i.getStatus(),
                i.getTargetType(), i.getTargetId(), targetLabel,
                i.getCreatedAt(), i.getUpdatedAt(), dtoMessages);
    }

    private String resolveCustomerName(Long customerId) {
        return customerRepository.findById(customerId)
                .map(c -> c.getNameLast() + " " + c.getNameFirst())
                .orElse("（不明な顧客）");
    }

    private String resolveSenderName(InquiryMessage m, Map<String, String> cache) {
        String cacheKey = m.getSenderType() + ":" + m.getSenderId();
        if (cache.containsKey(cacheKey)) return cache.get(cacheKey);
        String name;
        if (SENDER_TYPE_CUSTOMER.equals(m.getSenderType())) {
            name = customerRepository.findById(m.getSenderId())
                    .map(c -> c.getNameLast() + " " + c.getNameFirst())
                    .orElse("（不明な顧客）");
        } else if (SENDER_TYPE_ADMIN.equals(m.getSenderType())) {
            name = userRepository.findById(m.getSenderId())
                    .map(u -> u.getName() != null ? u.getName() : u.getEmail())
                    .orElse("（不明な管理者）");
        } else {
            name = "（不明）";
        }
        cache.put(cacheKey, name);
        return name;
    }
}
