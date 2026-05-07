package com.example.delivery.service;

import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * 配送予定日変更 Service（手動更新 / RRR-5）。
 *
 * <p>Service 層が {@code [manual]} プレフィックスを {@code operation_logs.comment} 先頭に
 * 自動付与する。集計時に reason カテゴリで絞り込めるようにするため。
 */
@Service
public class UpdateScheduledDateService {

    private static final String ACTION = "update_scheduled_date";
    private static final String TARGET_TYPE = "deliveries";
    private static final String SCREEN_NAME = "console.delivery.update_scheduled_date";
    private static final String API_NAME = "PATCH /api/deliveries/:id/scheduled-date";

    private final DeliveryRepository deliveryRepository;
    private final OperationLogRepository operationLogRepository;

    private final String manualPrefix;

    public UpdateScheduledDateService(DeliveryRepository deliveryRepository,
                                      OperationLogRepository operationLogRepository,
                                      @Value("${amazia.delivery.scheduled-date-reasons.manual}") String manualPrefix) {
        this.deliveryRepository = deliveryRepository;
        this.operationLogRepository = operationLogRepository;
        this.manualPrefix = manualPrefix;
    }

    @Transactional
    public Delivery update(Long deliveryId, LocalDate newDate, String reason, Long actorUserId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "delivery not found"));

        LocalDate oldDate = delivery.getScheduledDate();
        delivery.setScheduledDate(newDate);
        Delivery saved = deliveryRepository.saveAndFlush(delivery);

        String comment = manualPrefix
                + " 旧:" + (oldDate == null ? "NULL" : oldDate)
                + " → 新:" + newDate
                + (reason == null || reason.isBlank() ? "" : " / " + reason);
        recordLog(actorUserId, deliveryId, comment);
        return saved;
    }

    private void recordLog(Long actorUserId, Long deliveryId, String comment) {
        OperationLog log = new OperationLog();
        log.setUserId(actorUserId);
        log.setAction(ACTION);
        log.setTargetType(TARGET_TYPE);
        log.setTargetId(deliveryId);
        log.setScreenName(SCREEN_NAME);
        log.setApiName(API_NAME);
        log.setComment(comment);
        operationLogRepository.save(log);
    }
}
