package com.example.inquiry;

import com.example.inquiry.entity.Inquiry;
import com.example.inquiry.entity.InquiryMessage;
import com.example.inquiry.repository.InquiryMessageRepository;
import com.example.inquiry.repository.InquiryRepository;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ18 Step 1: Inquiry / InquiryMessage Entity / Repository の永続化と検索クエリ検証。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class InquiryRepositoryTest {

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private InquiryMessageRepository messageRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Long customerId;
    private Long otherCustomerId;

    @BeforeEach
    void setUp() {
        Customer c = newCustomer("山田", "太郎", "yamada-test@example.com");
        customerId = customerRepository.saveAndFlush(c).getId();

        Customer c2 = newCustomer("佐藤", "花子", "sato-test@example.com");
        otherCustomerId = customerRepository.saveAndFlush(c2).getId();
    }

    @Test
    void save_すると_id_と_created_at_updated_at_と_status_既定値が設定される() {
        Inquiry i = newInquiry(customerId, "件名");
        i.setStatus(null); // 既定値 NEW を当てに行く

        Inquiry saved = inquiryRepository.saveAndFlush(i);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals("NEW", saved.getStatus());
    }

    @Test
    void countByStatus_NEW_は_status_NEW_の件数を返す() {
        inquiryRepository.saveAndFlush(newInquiry(customerId, "件名 1"));
        Inquiry i2 = newInquiry(customerId, "件名 2");
        i2.setStatus("IN_PROGRESS");
        inquiryRepository.saveAndFlush(i2);
        Inquiry i3 = newInquiry(customerId, "件名 3");
        i3.setStatus("DONE");
        inquiryRepository.saveAndFlush(i3);

        long count = inquiryRepository.countByStatus("NEW");

        assertEquals(1, count);
    }

    @Test
    void searchForConsole_status_filter_が動作する() {
        inquiryRepository.saveAndFlush(newInquiry(customerId, "未対応のもの"));
        Inquiry i2 = newInquiry(customerId, "完了したもの");
        i2.setStatus("DONE");
        inquiryRepository.saveAndFlush(i2);

        Page<Inquiry> page = inquiryRepository.searchForConsole(
                "DONE", null, null, null, null, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("完了したもの", page.getContent().get(0).getSubject());
    }

    @Test
    void searchForConsole_userName_部分一致が動作する() {
        // 山田 太郎 と 佐藤 花子 はそれぞれ顧客
        inquiryRepository.saveAndFlush(newInquiry(customerId, "山田の問い合わせ"));
        inquiryRepository.saveAndFlush(newInquiry(otherCustomerId, "佐藤の問い合わせ"));

        Page<Inquiry> page = inquiryRepository.searchForConsole(
                null, null, null, null, "山田", PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("山田の問い合わせ", page.getContent().get(0).getSubject());
    }

    @Test
    void findByUserIdOrderByUpdatedAtDesc_は自分のものだけを返す() {
        inquiryRepository.saveAndFlush(newInquiry(customerId, "自分の問い合わせ A"));
        inquiryRepository.saveAndFlush(newInquiry(customerId, "自分の問い合わせ B"));
        inquiryRepository.saveAndFlush(newInquiry(otherCustomerId, "他人の問い合わせ"));

        Page<Inquiry> page = inquiryRepository.findByUserIdOrderByUpdatedAtDesc(
                customerId, PageRequest.of(0, 20));

        assertEquals(2, page.getTotalElements());
        page.getContent().forEach(i -> assertEquals(customerId, i.getUserId()));
    }

    @Test
    void inquiry_message_save_すると_created_at_と_既定_isInternalNote_が設定される() {
        Inquiry inquiry = inquiryRepository.saveAndFlush(newInquiry(customerId, "親問い合わせ"));

        InquiryMessage msg = new InquiryMessage();
        msg.setInquiryId(inquiry.getId());
        msg.setSenderType("market_customer");
        msg.setSenderId(customerId);
        msg.setMessage("こんにちは");
        msg.setIsInternalNote(null);

        InquiryMessage saved = messageRepository.saveAndFlush(msg);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals(Boolean.FALSE, saved.getIsInternalNote());
    }

    @Test
    void messageRepository_findByInquiryIdAndIsInternalNoteFalse_は内部メモを除外する() {
        Inquiry inquiry = inquiryRepository.saveAndFlush(newInquiry(customerId, "親問い合わせ"));

        // 顧客メッセージ
        messageRepository.saveAndFlush(newMessage(inquiry.getId(), "market_customer", customerId,
                "顧客メッセージ", false));
        // 管理者の通常返信
        messageRepository.saveAndFlush(newMessage(inquiry.getId(), "admin_user", 1L,
                "管理者返信", false));
        // 管理者の内部メモ（market 側 API では除外される）
        messageRepository.saveAndFlush(newMessage(inquiry.getId(), "admin_user", 1L,
                "内部メモ", true));

        List<InquiryMessage> all =
                messageRepository.findByInquiryIdOrderByCreatedAtAsc(inquiry.getId());
        List<InquiryMessage> marketView =
                messageRepository.findByInquiryIdAndIsInternalNoteFalseOrderByCreatedAtAsc(inquiry.getId());

        assertEquals(3, all.size());
        assertEquals(2, marketView.size());
        marketView.forEach(m -> assertEquals(Boolean.FALSE, m.getIsInternalNote()));
    }

    private Inquiry newInquiry(Long userId, String subject) {
        Inquiry i = new Inquiry();
        i.setUserId(userId);
        i.setSubject(subject);
        i.setStatus("NEW");
        return i;
    }

    private InquiryMessage newMessage(Long inquiryId, String senderType, Long senderId,
                                      String body, boolean internalNote) {
        InquiryMessage m = new InquiryMessage();
        m.setInquiryId(inquiryId);
        m.setSenderType(senderType);
        m.setSenderId(senderId);
        m.setMessage(body);
        m.setIsInternalNote(internalNote);
        return m;
    }

    private Customer newCustomer(String last, String first, String email) {
        Customer c = new Customer();
        c.setNameLast(last);
        c.setNameFirst(first);
        c.setPostalCode("100-0001");
        c.setAddress("東京都");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail(email);
        c.setPasswordHash("dummyhash");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        return c;
    }
}
