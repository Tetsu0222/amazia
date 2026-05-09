package com.example.inquiry.service;

import com.example.inquiry.dto.MarketCreateInquiryRequest;
import com.example.inquiry.entity.Inquiry;
import com.example.inquiry.entity.InquiryMessage;
import com.example.inquiry.exception.InquiryValidationException;
import com.example.inquiry.repository.InquiryMessageRepository;
import com.example.inquiry.repository.InquiryRepository;
import com.example.inquiry.service.notification.InquiryNotificationDispatcher;
import com.example.inquiry.validator.InquiryTargetOwnershipValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Market 顧客の問い合わせ新規作成（フェーズ18 / 設計書 §2.2.1）。
 *
 * <p>同一トランザクションで {@code inquiries} + 初回 {@code inquiry_messages} を INSERT し、
 * {@link InquiryNotificationDispatcher#dispatchCreated} を呼ぶ。通知側は phase17 §6.4 の
 * {@code REQUIRES_NEW} で別トランザクションに分離されている。
 */
@Service
public class CreateInquiryService {

    private final InquiryRepository inquiryRepository;
    private final InquiryMessageRepository messageRepository;
    private final InquiryTargetOwnershipValidator targetValidator;
    private final InquiryNotificationDispatcher notificationDispatcher;

    @Value("${amazia.inquiry.subject-max-length}")
    private int subjectMaxLength;

    @Value("${amazia.inquiry.message-max-length}")
    private int messageMaxLength;

    public CreateInquiryService(InquiryRepository inquiryRepository,
                                InquiryMessageRepository messageRepository,
                                InquiryTargetOwnershipValidator targetValidator,
                                InquiryNotificationDispatcher notificationDispatcher) {
        this.inquiryRepository = inquiryRepository;
        this.messageRepository = messageRepository;
        this.targetValidator = targetValidator;
        this.notificationDispatcher = notificationDispatcher;
    }

    @Transactional
    public Long create(MarketCreateInquiryRequest req, Long marketCustomerId) {
        validateLengths(req);
        targetValidator.validate(req.targetType(), req.targetId(), marketCustomerId);

        Inquiry inquiry = new Inquiry();
        inquiry.setUserId(marketCustomerId);
        inquiry.setSubject(req.subject());
        inquiry.setStatus("NEW");
        inquiry.setTargetType(req.targetType());
        inquiry.setTargetId(req.targetId());
        inquiry = inquiryRepository.save(inquiry);

        InquiryMessage msg = new InquiryMessage();
        msg.setInquiryId(inquiry.getId());
        msg.setSenderType("market_customer");
        msg.setSenderId(marketCustomerId);
        msg.setMessage(req.message());
        msg.setIsInternalNote(false);
        messageRepository.save(msg);

        notificationDispatcher.dispatchCreated(inquiry);
        return inquiry.getId();
    }

    private void validateLengths(MarketCreateInquiryRequest req) {
        if (req.subject() != null && req.subject().length() > subjectMaxLength) {
            throw new InquiryValidationException(
                    "subject exceeds max length: " + subjectMaxLength);
        }
        if (req.message() != null && req.message().length() > messageMaxLength) {
            throw new InquiryValidationException(
                    "message exceeds max length: " + messageMaxLength);
        }
    }
}
