package com.example.delivery.service;

import com.example.address.entity.Address;
import com.example.address.repository.AddressRepository;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 配送先住所変更 Service（フェーズ15 r5 / RRR-7）。
 *
 * <p>{@code sales.user_id} が所有する {@code address} のみ参照可能を Service 層で強制する。
 * オーナー外の {@code address.id} 指定は {@code 403 FORBIDDEN}。
 * 既に DELIVERED / RETURN_REQUESTED / RETURNED の delivery は変更不可（{@code 409 CONFLICT}）。
 */
@Service
public class UpdateShippingAddressService {

    private static final String ACTION = "update_shipping_address";
    private static final String TARGET_TYPE = "deliveries";
    private static final String SCREEN_NAME = "console.delivery.update_address";
    private static final String API_NAME = "PATCH /api/deliveries/:id/address";

    private final DeliveryRepository deliveryRepository;
    private final SalesRepository salesRepository;
    private final AddressRepository addressRepository;
    private final OperationLogRepository operationLogRepository;

    public UpdateShippingAddressService(DeliveryRepository deliveryRepository,
                                        SalesRepository salesRepository,
                                        AddressRepository addressRepository,
                                        OperationLogRepository operationLogRepository) {
        this.deliveryRepository = deliveryRepository;
        this.salesRepository = salesRepository;
        this.addressRepository = addressRepository;
        this.operationLogRepository = operationLogRepository;
    }

    @Transactional
    public Delivery update(Long deliveryId, Long newShippingAddressId, String reason, Long actorUserId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "delivery not found"));

        Sales sales = salesRepository.findById(delivery.getSalesId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sales not found"));

        Address address = addressRepository.findById(newShippingAddressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "address not found"));

        // オーナー検証（RRR-7）
        if (!address.getUserId().equals(sales.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "address does not belong to sales owner");
        }
        if (!address.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "address is not active");
        }

        Long oldAddressId = delivery.getShippingAddressId();
        delivery.setShippingAddressId(newShippingAddressId);
        Delivery saved = deliveryRepository.saveAndFlush(delivery);

        recordLog(actorUserId, deliveryId,
                "旧 address_id: " + oldAddressId
                        + " → 新 address_id: " + newShippingAddressId
                        + (reason == null || reason.isBlank() ? "" : " / 理由: " + reason));
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
