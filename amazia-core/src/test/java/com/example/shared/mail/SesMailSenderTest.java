package com.example.shared.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * フェーズ17 Step 7 / 設計書 §6.1 / §6.2.2：
 * <ul>
 *   <li>WARN/ERROR テンプレのみ SES 送出される</li>
 *   <li>INFO テンプレは送出をスキップ（戻り値 false）</li>
 *   <li>SES 例外は MailSendException に整形される（BatchRetryClassifier のリトライ経路）</li>
 *   <li>sendRaw は subject / body をそのまま流し、INFO のみ早期 return する</li>
 * </ul>
 */
class SesMailSenderTest {

    private SesClient sesClient;
    private MailTemplateLoader templateLoader;
    private SesMailSender sender;

    @BeforeEach
    void setUp() {
        sesClient = mock(SesClient.class);
        templateLoader = new MailTemplateLoader();
        templateLoader.load();
        sender = new SesMailSender(sesClient, templateLoader, "no-reply@amazia.test");
    }

    @Test
    void SES_1_WARN_テンプレで_SES_に_subject_と_body_が_差込済で渡る() {
        boolean sent = sender.send("batch_inventory_inconsistency", "admin@example.com",
                Map.of("productId", "42", "productName", "テスト商品",
                        "warehouseId", "1", "inventoryQty", "10",
                        "skuTotalQty", "8", "diffQty", "-2",
                        "executedAt", "2026-05-08T03:30",
                        "jobName", "InventoryConsistencyCheckJob",
                        "batchExecutionId", "999",
                        "consoleUrl", "https://console.example/notifications"));
        assertTrue(sent);
        ArgumentCaptor<SendEmailRequest> req = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(req.capture());
        SendEmailRequest captured = req.getValue();
        assertEquals("no-reply@amazia.test", captured.source());
        assertEquals(1, captured.destination().toAddresses().size());
        assertEquals("admin@example.com", captured.destination().toAddresses().get(0));
        String subject = captured.message().subject().data();
        String body = captured.message().body().text().data();
        assertTrue(subject.contains("商品ID=42"), "subject placeholder unresolved: " + subject);
        assertTrue(body.contains("商品ID    : 42"), "body placeholder unresolved");
        assertTrue(body.contains("差分      : -2"));
    }

    @Test
    void SES_2_INFO_レベルは送出されず_false_を返す() {
        // 既存の YAML テンプレに INFO はないので INFO レベルの仮テンプレを別途検証する。
        // sendRaw 経路で INFO 指定時にスキップされることを確認する。
        boolean sent = sender.sendRaw("admin@example.com", "INFO", "件名", "本文");
        assertFalse(sent);
        verifyNoInteractions(sesClient);
    }

    @Test
    void SES_3_SES_例外は_MailSendException_に整形される() {
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(SesException.builder().message("throttled").build());
        BatchMailSendException ex = assertThrows(BatchMailSendException.class,
                () -> sender.send("batch_job_failed", "admin@example.com",
                        Map.of("jobName", "X", "batchExecutionId", "1",
                                "status", "FAILED", "startedAt", "t",
                                "finishedAt", "t", "exceptionClass", "E",
                                "errorMessage", "boom", "consoleUrl", "u")));
        assertTrue(ex.getMessage().contains("template=batch_job_failed"));
    }

    @Test
    void SES_4_sendRaw_は_subject_body_をそのまま流す() {
        boolean sent = sender.sendRaw("u@example.com", "WARN", "S", "B");
        assertTrue(sent);
        ArgumentCaptor<SendEmailRequest> req = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(req.capture());
        assertEquals("S", req.getValue().message().subject().data());
        assertEquals("B", req.getValue().message().body().text().data());
    }
}
