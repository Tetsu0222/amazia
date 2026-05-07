package com.example.delivery.service;

import com.example.sales.entity.Sales;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 配送予定日の計算（フェーズ15 r5 / R-6 / RR-2 / RRR-5 / RRRR-4）。
 *
 * <p>計算ロジックは規約 1-1 に従い本 Service に集約する。
 * 都道府県別リードタイムは phaseX-5（マスタ化）に切り出し。
 * 本フェーズでは {@code shipping_methods} × 全国一律のリードタイムで算出する。
 *
 * <p>呼び出し元：
 * <ul>
 *   <li>{@code DeliveryCreationService}（注文確定時の初回計算）— stock 基準</li>
 *   <li>{@code DeliveryRescheduleService}（入荷再計算）— 在庫を Service 内ローカル変数で消費トラッキング</li>
 * </ul>
 */
@Service
public class DeliveryScheduleService {

    private final long homeDeliveryId;
    private final long konbiniPickupId;
    private final long dropoffId;
    private final int homeDeliveryDays;
    private final int konbiniPickupDays;
    private final int dropoffDays;

    public DeliveryScheduleService(
            @Value("${amazia.delivery.shipping-methods.home-delivery-id}") long homeDeliveryId,
            @Value("${amazia.delivery.shipping-methods.konbini-pickup-id}") long konbiniPickupId,
            @Value("${amazia.delivery.shipping-methods.dropoff-id}") long dropoffId,
            @Value("${amazia.delivery.lead-time-days.home-delivery}") int homeDeliveryDays,
            @Value("${amazia.delivery.lead-time-days.konbini-pickup}") int konbiniPickupDays,
            @Value("${amazia.delivery.lead-time-days.dropoff}") int dropoffDays) {
        this.homeDeliveryId = homeDeliveryId;
        this.konbiniPickupId = konbiniPickupId;
        this.dropoffId = dropoffId;
        this.homeDeliveryDays = homeDeliveryDays;
        this.konbiniPickupDays = konbiniPickupDays;
        this.dropoffDays = dropoffDays;
    }

    /**
     * 配送予定日を算出する。
     *
     * @param sales          売上レコード（注文日・配送方法を参照）
     * @param stockAvailable 算出時点の在庫（在庫切れの場合は null を返す）
     * @return 配送予定日。在庫切れ（{@code stockAvailable < sales.quantity}）のときは null
     */
    public LocalDate calculate(Sales sales, int stockAvailable) {
        if (stockAvailable < sales.getQuantity()) {
            return null;
        }
        int leadTimeDays = leadTimeDaysFor(sales.getShippingMethodId());
        return sales.getSalesDate().plusDays(leadTimeDays);
    }

    private int leadTimeDaysFor(long shippingMethodId) {
        if (shippingMethodId == homeDeliveryId)  return homeDeliveryDays;
        if (shippingMethodId == konbiniPickupId) return konbiniPickupDays;
        if (shippingMethodId == dropoffId)       return dropoffDays;
        // マスタに無い ID は防御的に home_delivery のリードタイムにフォールバック
        return homeDeliveryDays;
    }
}
