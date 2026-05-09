package com.example.inquiry.repository;

import com.example.inquiry.entity.InquiryMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 問い合わせメッセージ Repository（フェーズ18 r3 / 設計書 §3.2）。
 */
public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {

    /** Console 詳細：内部メモも含めて時系列順 */
    List<InquiryMessage> findByInquiryIdOrderByCreatedAtAsc(Long inquiryId);

    /** Market 詳細：内部メモを除外して時系列順 */
    List<InquiryMessage> findByInquiryIdAndIsInternalNoteFalseOrderByCreatedAtAsc(Long inquiryId);
}
