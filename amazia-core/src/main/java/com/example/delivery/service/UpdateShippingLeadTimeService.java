package com.example.delivery.service;

import com.example.delivery.dto.ShippingLeadTimeResponse;
import com.example.delivery.entity.ShippingLeadTime;
import com.example.delivery.repository.ShippingLeadTimeRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 都道府県別リードタイム更新 Service（フェーズX-5）。
 *
 * <p>{@code lead_time_days = 0} は許容（無効化運用 / 設計書 §設計上の注意 line 120）。
 * {@code operation_logs} に {@code action=update_shipping_lead_time} を記録する。
 */
@Service
public class UpdateShippingLeadTimeService {

    private static final String ACTION = "update_shipping_lead_time";
    private static final String TARGET_TYPE = "shipping_lead_times";
    private static final String SCREEN_NAME = "console.shipping_lead_time.update";
    private static final String API_NAME = "PATCH /api/shipping-lead-times/:id";

    private final ShippingLeadTimeRepository repository;
    private final OperationLogRepository operationLogRepository;

    public UpdateShippingLeadTimeService(ShippingLeadTimeRepository repository,
                                         OperationLogRepository operationLogRepository) {
        this.repository = repository;
        this.operationLogRepository = operationLogRepository;
    }

    @Transactional
    public ShippingLeadTimeResponse update(Long id, int newLeadTimeDays, Long actorUserId) {
        if (newLeadTimeDays < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "leadTimeDays must be >= 0");
        }
        ShippingLeadTime entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "shipping_lead_time not found"));

        int oldDays = entity.getLeadTimeDays();
        entity.setLeadTimeDays(newLeadTimeDays);
        ShippingLeadTime saved = repository.saveAndFlush(entity);

        String comment = "method_id=" + saved.getShippingMethodId()
                + " / prefecture=" + saved.getPrefecture()
                + " / 旧:" + oldDays + "日 → 新:" + newLeadTimeDays + "日";
        recordLog(actorUserId, id, comment);
        return new ShippingLeadTimeResponse(saved);
    }

    private void recordLog(Long actorUserId, Long targetId, String comment) {
        OperationLog log = new OperationLog();
        log.setUserId(actorUserId);
        log.setAction(ACTION);
        log.setTargetType(TARGET_TYPE);
        log.setTargetId(targetId);
        log.setScreenName(SCREEN_NAME);
        log.setApiName(API_NAME);
        log.setComment(comment);
        operationLogRepository.save(log);
    }
}
