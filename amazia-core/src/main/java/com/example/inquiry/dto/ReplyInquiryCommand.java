package com.example.inquiry.dto;

/**
 * Service 内部の返信投稿コマンド（フェーズ18 / RV-11 / RV2-4）。
 *
 * <p>Market / Console 両用。Controller から Service へ渡すときに {@code senderType} / {@code senderId}
 * をセッション情報から組み立てる。{@code isInternalNote=true} は admin_user のみ許容（Service 層で再確認）。
 */
public record ReplyInquiryCommand(
        Long inquiryId,
        String senderType,
        Long senderId,
        String message,
        boolean isInternalNote
        // 将来：Long assignedUserId（未割当時の自動セット）
) {
}
