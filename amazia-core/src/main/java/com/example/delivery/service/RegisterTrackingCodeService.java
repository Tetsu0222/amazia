package com.example.delivery.service;

import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 追跡番号登録 Service。
 */
@Service
public class RegisterTrackingCodeService {

    private static final String ACTION = "register_tracking_code";
    private static final String TARGET_TYPE = "deliveries";
    private static final String SCREEN_NAME = "console.delivery.register_tracking";
    private static final String API_NAME = "PATCH /api/deliveries/:id/tracking-code";

    private final DeliveryRepository deliveryRepository;
    private final OperationLogRepository operationLogRepository;

    public RegisterTrackingCodeService(DeliveryRepository deliveryRepository,
                                       OperationLogRepository operationLogRepository) {
        this.deliveryRepository = deliveryRepository;
        this.operationLogRepository = operationLogRepository;
    }

    @Transactional
    public Delivery register(Long deliveryId, String trackingCode, Long actorUserId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "delivery not found"));
        delivery.setTrackingCode(trackingCode);
        Delivery saved = deliveryRepository.saveAndFlush(delivery);

        recordLog(actorUserId, deliveryId, "追跡番号: " + trackingCode);
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
