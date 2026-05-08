package com.example.batch;

import com.example.batch.entity.BatchExecution;
import com.example.batch.job.DeliveryStatusAdvanceJob;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 3-4: DeliveryStatusAdvanceJob の TDD（設計書 §3.1 ④ / 計画書 §4-4）。
 *
 * <p>**遷移しない**ことの確認が主目的。delivery 行を作成しても shipping_status_id は変わらない。
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class DeliveryStatusAdvanceJobTest {

    @Autowired private DeliveryStatusAdvanceJob job;
    @Autowired private BatchExecutionRepository batchExecutionRepository;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private ConsoleNotificationRepository consoleNotificationRepository;

    @Value("${amazia.sales.shipping-statuses.shipped-id}") private long shippedStatusId;

    @Test
    void DEL_1_SHIPPED_かつ_scheduledDate_が_8日前なら遅延候補として通知され遷移はしない() {
        long beforeNotifications = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc(
                        DeliveryStatusAdvanceJob.SUBSCRIPTION_TAG).size();
        Long deliveryId = persistDelivery(shippedStatusId, LocalDate.now().minusDays(8), null);

        job.run("scheduler");

        BatchExecution exec = latestExecution();
        assertEquals("SUCCESS", exec.getStatus());

        // 遷移していない
        Delivery d = deliveryRepository.findById(deliveryId).orElseThrow();
        assertEquals(shippedStatusId, d.getShippingStatusId(), "バッチは状態遷移しない");

        long after = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc(
                        DeliveryStatusAdvanceJob.SUBSCRIPTION_TAG).size();
        assertTrue(after >= beforeNotifications + 1L, "遅延候補は delivery_alerts へ通知される");
    }

    @Test
    void DEL_2_SHIPPED_かつ_scheduledDate_が_3日前なら通知対象に入らない() {
        long beforeNotifications = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc(
                        DeliveryStatusAdvanceJob.SUBSCRIPTION_TAG).size();
        Long deliveryId = persistDelivery(shippedStatusId, LocalDate.now().minusDays(3), null);

        job.run("scheduler");

        // この delivery の遷移はしないし、遅延候補にも入らない
        Delivery d = deliveryRepository.findById(deliveryId).orElseThrow();
        assertEquals(shippedStatusId, d.getShippingStatusId());

        long after = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc(
                        DeliveryStatusAdvanceJob.SUBSCRIPTION_TAG).size();
        // この delivery 起因の追加 1 件分はないこと（他要因の増加は許容）
        // 遅延閾値以下なので bodyに本 deliveryId は出ない
        consoleNotificationRepository.findAll().stream()
                .filter(n -> n.getBody() != null && n.getBody().contains("配送 ID " + deliveryId))
                .findAny()
                .ifPresent(n -> fail("3 日前は遅延扱いではないため通知が出てはいけない"));
        assertTrue(after >= beforeNotifications);
    }

    private Long persistDelivery(long statusId, LocalDate scheduled, LocalDate delivered) {
        Delivery d = new Delivery();
        // sales_id は UNIQUE 制約のためテスト毎にユニークな値を使う
        d.setSalesId(System.nanoTime());
        d.setShippingAddressId(1L);
        d.setShippingMethodId(1L);
        d.setShippingStatusId(statusId);
        d.setScheduledDate(scheduled);
        d.setDeliveredDate(delivered);
        return deliveryRepository.saveAndFlush(d).getId();
    }

    private BatchExecution latestExecution() {
        return batchExecutionRepository
                .findByJobNameOrderByStartedAtDesc(DeliveryStatusAdvanceJob.JOB_NAME)
                .get(0);
    }
}
