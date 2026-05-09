package com.example.inquiry.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ18 Step 2: Market 用 POST DTO に is_internal_note 系フィールドが
 * 構造的に存在しないことを保証する（DTO-1 / RV-9）。
 *
 * <p>リフレクションで Record コンポーネントを列挙し、is_internal_note を意図する
 * フィールド名（isInternalNote / internal_note 等）が含まれないことを検証する。
 * Mass Assignment 攻撃を Controller 入口で塞ぐ防御線。
 */
class MarketDtoStructureTest {

    @Test
    void MarketCreateInquiryRequest_は_isInternalNote_を持たない() {
        assertNoInternalNoteField(MarketCreateInquiryRequest.class);
    }

    @Test
    void MarketReplyInquiryRequest_は_isInternalNote_を持たない() {
        assertNoInternalNoteField(MarketReplyInquiryRequest.class);
    }

    @Test
    void ConsoleReplyInquiryRequest_は_isInternalNote_を持つ() {
        // 反対の検証：Console 用は持つこと
        boolean has = Arrays.stream(ConsoleReplyInquiryRequest.class.getRecordComponents())
                .anyMatch(rc -> rc.getName().equals("isInternalNote"));
        assertTrue(has, "ConsoleReplyInquiryRequest must have isInternalNote field");
    }

    private void assertNoInternalNoteField(Class<?> recordClass) {
        boolean has = Arrays.stream(recordClass.getRecordComponents())
                .anyMatch(rc -> rc.getName().toLowerCase()
                        .contains("internalnote")
                        || rc.getName().toLowerCase().contains("internal_note"));
        assertFalse(has,
                recordClass.getSimpleName()
                        + " must NOT have isInternalNote / internal_note field (RV-9 / Mass Assignment 防御)");
    }
}
