package com.example.inquiry.service.notification;

import com.example.inquiry.entity.Inquiry;
import com.example.inquiry.service.InquiryTargetLabelResolver;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.notification.service.BatchAlertNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 問い合わせドメインの通知発火点（フェーズ18 / RV-1 / RV-2 / 設計書 §6.1）。
 *
 * <p>phase17 {@link BatchAlertNotifier#dispatch} を呼び出し、{@code subscription_tag='inquiry_alerts'}、
 * {@code level='INFO'} で {@code console_notifications} に INSERT する（SES は INFO のため送出されない）。
 *
 * <p>{@code payloadIdentity} は新規/返信/ステータス変更で各々生成（RV-2：messages.id を含めない）。
 * 重複抑制（60 分・{@code payload_hash} ベース）は phase17 §6.4 で既実装。
 */
@Component
public class InquiryNotificationDispatcher {

    private static final String LEVEL_INFO = "INFO";

    private final BatchAlertNotifier batchAlertNotifier;
    private final InquiryTargetLabelResolver targetLabelResolver;
    private final CustomerRepository customerRepository;

    @Value("${amazia.inquiry.notification-tag}")
    private String tag;

    @Value("${amazia.inquiry.notification-templates.created.title}")
    private String createdTitleTpl;
    @Value("${amazia.inquiry.notification-templates.created.body}")
    private String createdBodyTpl;
    @Value("${amazia.inquiry.notification-templates.replied.title}")
    private String repliedTitleTpl;
    @Value("${amazia.inquiry.notification-templates.replied.body}")
    private String repliedBodyTpl;
    @Value("${amazia.inquiry.notification-templates.status-changed.title}")
    private String statusChangedTitleTpl;
    @Value("${amazia.inquiry.notification-templates.status-changed.body}")
    private String statusChangedBodyTpl;

    public InquiryNotificationDispatcher(BatchAlertNotifier batchAlertNotifier,
                                         InquiryTargetLabelResolver targetLabelResolver,
                                         CustomerRepository customerRepository) {
        this.batchAlertNotifier = batchAlertNotifier;
        this.targetLabelResolver = targetLabelResolver;
        this.customerRepository = customerRepository;
    }

    /** 新規作成時：payloadIdentity = "inquiry_created:" + inquiry_id */
    public void dispatchCreated(Inquiry inquiry) {
        Map<String, String> vars = baseVars(inquiry);
        String title = render(createdTitleTpl, vars);
        String body  = render(createdBodyTpl, vars);
        String payloadIdentity = "inquiry_created:" + inquiry.getId();
        batchAlertNotifier.dispatch(LEVEL_INFO, tag, title, body, payloadIdentity, null, null);
    }

    /** 顧客返信時：payloadIdentity = "inquiry_replied:" + inquiry_id（messages.id を含めない / RV-2） */
    public void dispatchReplied(Inquiry inquiry) {
        Map<String, String> vars = baseVars(inquiry);
        String title = render(repliedTitleTpl, vars);
        String body  = render(repliedBodyTpl, vars);
        String payloadIdentity = "inquiry_replied:" + inquiry.getId();
        batchAlertNotifier.dispatch(LEVEL_INFO, tag, title, body, payloadIdentity, null, null);
    }

    /** ステータス変更時：payloadIdentity = "inquiry_status:" + inquiry_id + ":" + new_status */
    public void dispatchStatusChanged(Inquiry inquiry, String oldStatus, String newStatus) {
        Map<String, String> vars = baseVars(inquiry);
        vars.put("old_status", oldStatus);
        vars.put("new_status", newStatus);
        String title = render(statusChangedTitleTpl, vars);
        String body  = render(statusChangedBodyTpl, vars);
        String payloadIdentity = "inquiry_status:" + inquiry.getId() + ":" + newStatus;
        batchAlertNotifier.dispatch(LEVEL_INFO, tag, title, body, payloadIdentity, null, null);
    }

    private Map<String, String> baseVars(Inquiry inquiry) {
        Map<String, String> vars = new HashMap<>();
        vars.put("inquiry_id",   String.valueOf(inquiry.getId()));
        vars.put("subject",      inquiry.getSubject());
        vars.put("user_name",    resolveUserName(inquiry.getUserId()));
        vars.put("target_label", targetLabelResolver.resolve(inquiry.getTargetType(), inquiry.getTargetId()));
        return vars;
    }

    private String resolveUserName(Long customerId) {
        return customerRepository.findById(customerId)
                .map(c -> c.getNameLast() + " " + c.getNameFirst())
                .orElse("（不明な顧客）");
    }

    private String render(String tpl, Map<String, String> vars) {
        String s = tpl;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return s;
    }
}
