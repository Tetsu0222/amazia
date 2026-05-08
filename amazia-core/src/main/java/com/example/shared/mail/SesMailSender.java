package com.example.shared.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * フェーズ17 Step 7 / 設計書 §6.1 / §6.2.2：
 * バッチ通知用のメール送信入口。テンプレート展開と「WARN/ERROR のみ送出」フィルタを担う。
 *
 * <ul>
 *   <li>{@link MailTemplateLoader} からテンプレ取得・プレースホルダ展開</li>
 *   <li>{@code level} が {@code INFO} の場合は送信しない（設計書 §6.2.2 の第二条件）</li>
 *   <li>SES 失敗は {@link BatchMailSendException} に包んで上位へ伝播し、
 *       {@code BatchRetryClassifier} のリトライ経路に乗せる</li>
 * </ul>
 *
 * <p>本クラスは「テンプレ・宛先・差込値が揃っている前提」の薄いラッパー。
 * 購読者解決（{@code notification_subscriptions} の検索）は呼び出し側の責務（{@code BatchAlertNotifier}）。
 */
@Component
public class SesMailSender {

    private static final Logger log = LoggerFactory.getLogger(SesMailSender.class);

    private final SesClient sesClient;
    private final MailTemplateLoader templateLoader;
    private final String fromAddress;

    public SesMailSender(SesClient sesClient,
                         MailTemplateLoader templateLoader,
                         @Value("${aws.ses.from-address}") String fromAddress) {
        this.sesClient = sesClient;
        this.templateLoader = templateLoader;
        this.fromAddress = fromAddress;
    }

    /**
     * 指定テンプレを差込値で展開し、宛先 1 件へ送信する。
     *
     * @return {@code true} = 送信した、{@code false} = レベル INFO のため意図的にスキップ
     * @throws BatchMailSendException SES 送信に失敗した場合（リトライ対象として上位へ伝播）
     */
    public boolean send(String templateId, String to, Map<String, String> values) {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(to, "to");

        MailTemplate template = templateLoader.get(templateId);
        if (isInfoLevel(template.level())) {
            log.debug("SES skipped (INFO level) template={} to={}", templateId, to);
            return false;
        }

        String subject = templateLoader.render(template.subject(), values);
        String body = templateLoader.render(template.body(), values);
        sendInternal(to, subject, body, "template=" + templateId);
        return true;
    }

    /**
     * subject / body が既にレンダリング済みの場合の直接送信入口（{@code BatchAlertNotifier} の
     * 既存 dispatch 経路向け）。INFO レベルはスキップする責務を本クラスに集約する。
     *
     * @return {@code true} = 送信した、{@code false} = レベル INFO のため意図的にスキップ
     */
    public boolean sendRaw(String to, String level, String subject, String body) {
        Objects.requireNonNull(to, "to");
        if (isInfoLevel(level)) {
            log.debug("SES skipped (INFO level) to={}", to);
            return false;
        }
        sendInternal(to, subject != null ? subject : "", body != null ? body : "", "raw");
        return true;
    }

    private void sendInternal(String to, String subject, String body, String contextTag) {
        try {
            sesClient.sendEmail(SendEmailRequest.builder()
                    .source(fromAddress)
                    .destination(Destination.builder().toAddresses(to).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .text(Content.builder().data(body).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build());
        } catch (RuntimeException e) {
            // BatchRetryClassifier は BatchMailSendException をリトライ対象にしている（§3 / R-6）。
            throw new BatchMailSendException("SES send failed: " + contextTag + " to=" + to, e);
        }
    }

    private boolean isInfoLevel(String level) {
        return level != null && "INFO".equals(level.toUpperCase(Locale.ROOT));
    }
}
