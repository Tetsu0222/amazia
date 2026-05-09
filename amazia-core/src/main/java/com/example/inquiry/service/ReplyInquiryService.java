package com.example.inquiry.service;

import com.example.inquiry.dto.ReplyInquiryCommand;
import com.example.inquiry.entity.Inquiry;
import com.example.inquiry.entity.InquiryMessage;
import com.example.inquiry.exception.ForbiddenInquiryAccessException;
import com.example.inquiry.exception.InquiryNotFoundException;
import com.example.inquiry.exception.InquiryValidationException;
import com.example.inquiry.repository.InquiryMessageRepository;
import com.example.inquiry.repository.InquiryRepository;
import com.example.inquiry.service.notification.InquiryNotificationDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 問い合わせへの返信投稿（Market / Console 両用 / フェーズ18）。
 *
 * <p>Market 顧客は自身の問い合わせのみ。Console 管理者は内部メモ ({@code is_internal_note=true}) も可。
 * 顧客返信時のみ通知を発火する（管理者返信時は status 不変・通知なし方針）。
 */
@Service
public class ReplyInquiryService {

    private static final String SENDER_TYPE_CUSTOMER = "market_customer";
    private static final String SENDER_TYPE_ADMIN    = "admin_user";

    private final InquiryRepository inquiryRepository;
    private final InquiryMessageRepository messageRepository;
    private final InquiryNotificationDispatcher notificationDispatcher;

    @Value("${amazia.inquiry.message-max-length}")
    private int messageMaxLength;

    public ReplyInquiryService(InquiryRepository inquiryRepository,
                               InquiryMessageRepository messageRepository,
                               InquiryNotificationDispatcher notificationDispatcher) {
        this.inquiryRepository = inquiryRepository;
        this.messageRepository = messageRepository;
        this.notificationDispatcher = notificationDispatcher;
    }

    @Transactional
    public Long reply(ReplyInquiryCommand cmd) {
        validateMessage(cmd.message());

        Inquiry inquiry = inquiryRepository.findById(cmd.inquiryId())
                .orElseThrow(() -> new InquiryNotFoundException("inquiry not found: " + cmd.inquiryId()));

        // Market 顧客は自身の問い合わせのみ
        if (SENDER_TYPE_CUSTOMER.equals(cmd.senderType())
                && !Objects.equals(inquiry.getUserId(), cmd.senderId())) {
            throw new ForbiddenInquiryAccessException(
                    "inquiry does not belong to current customer");
        }

        // 内部メモは admin のみ（DTO 構造分離 + ここで再確認 / RV-9 二重防御）
        boolean isInternalNote = cmd.isInternalNote();
        if (isInternalNote && !SENDER_TYPE_ADMIN.equals(cmd.senderType())) {
            throw new InquiryValidationException("only admin can post internal note");
        }

        InquiryMessage msg = new InquiryMessage();
        msg.setInquiryId(cmd.inquiryId());
        msg.setSenderType(cmd.senderType());
        msg.setSenderId(cmd.senderId());
        msg.setMessage(cmd.message());
        msg.setIsInternalNote(isInternalNote);
        InquiryMessage saved = messageRepository.save(msg);

        // 親 inquiries.updated_at 更新（@PreUpdate に依存しないよう明示）
        inquiry.setUpdatedAt(LocalDateTime.now());
        inquiryRepository.save(inquiry);

        // 顧客返信時のみ通知発火（管理者返信は通知なし）
        if (SENDER_TYPE_CUSTOMER.equals(cmd.senderType()) && !isInternalNote) {
            notificationDispatcher.dispatchReplied(inquiry);
        }

        return saved.getId();
    }

    private void validateMessage(String message) {
        if (message != null && message.length() > messageMaxLength) {
            throw new InquiryValidationException(
                    "message exceeds max length: " + messageMaxLength);
        }
    }
}
