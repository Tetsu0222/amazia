package com.example.delivery.service;

import com.example.address.entity.Address;
import com.example.address.repository.AddressRepository;
import com.example.delivery.entity.ShippingLeadTime;
import com.example.delivery.repository.ShippingLeadTimeRepository;
import com.example.sales.entity.Sales;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 配送予定日の計算（フェーズ15 r5 / R-6 / RR-2 / RRR-5 / RRRR-4 + フェーズX-5）。
 *
 * <p>計算ロジックは規約 1-1 に従い本 Service に集約する。
 * フェーズX-5 で都道府県別リードタイム（{@code shipping_lead_times} マスタ）参照を導入。
 * マスタ未登録／{@code prefecture} 厳密不一致／NULL の場合は phase15 r5 の
 * {@code amazia.delivery.lead-time-days.*} 全国一律値にフォールバックする。
 *
 * <p>呼び出し元：
 * <ul>
 *   <li>{@code DeliveryCreationService}（注文確定時の初回計算）— stock 基準</li>
 *   <li>{@code DeliveryRescheduleService}（入荷再計算）— 在庫を Service 内ローカル変数で消費トラッキング</li>
 * </ul>
 */
@Service
public class DeliveryScheduleService {

    private final AddressRepository addressRepository;
    private final ShippingLeadTimeRepository shippingLeadTimeRepository;

    private final long homeDeliveryId;
    private final long konbiniPickupId;
    private final long dropoffId;
    private final int homeDeliveryDays;
    private final int konbiniPickupDays;
    private final int dropoffDays;

    public DeliveryScheduleService(
            AddressRepository addressRepository,
            ShippingLeadTimeRepository shippingLeadTimeRepository,
            @Value("${amazia.delivery.shipping-methods.home-delivery-id}") long homeDeliveryId,
            @Value("${amazia.delivery.shipping-methods.konbini-pickup-id}") long konbiniPickupId,
            @Value("${amazia.delivery.shipping-methods.dropoff-id}") long dropoffId,
            @Value("${amazia.delivery.lead-time-days.home-delivery}") int homeDeliveryDays,
            @Value("${amazia.delivery.lead-time-days.konbini-pickup}") int konbiniPickupDays,
            @Value("${amazia.delivery.lead-time-days.dropoff}") int dropoffDays) {
        this.addressRepository = addressRepository;
        this.shippingLeadTimeRepository = shippingLeadTimeRepository;
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
     * @param sales          売上レコード（注文日・配送方法・配送先住所を参照）
     * @param stockAvailable 算出時点の在庫（在庫切れの場合は null を返す）
     * @return 配送予定日。在庫切れ（{@code stockAvailable < sales.quantity}）のときは null
     */
    public LocalDate calculate(Sales sales, int stockAvailable) {
        if (stockAvailable < sales.getQuantity()) {
            return null;
        }
        int leadTime = resolveLeadTimeDays(sales.getShippingMethodId(), sales.getShippingAddressId());
        return sales.getSalesDate().plusDays(leadTime);
    }

    private int resolveLeadTimeDays(Long shippingMethodId, Long shippingAddressId) {
        String prefecture = lookupPrefecture(shippingAddressId);
        if (prefecture != null && !prefecture.isBlank()) {
            Optional<ShippingLeadTime> override =
                    shippingLeadTimeRepository.findByShippingMethodIdAndPrefecture(shippingMethodId, prefecture);
            if (override.isPresent()) {
                return override.get().getLeadTimeDays();
            }
        }
        return leadTimeDaysFor(shippingMethodId);
    }

    private String lookupPrefecture(Long shippingAddressId) {
        if (shippingAddressId == null) return null;
        return addressRepository.findById(shippingAddressId)
                .map(Address::getPrefecture)
                .orElse(null);
    }

    private int leadTimeDaysFor(long shippingMethodId) {
        if (shippingMethodId == homeDeliveryId)  return homeDeliveryDays;
        if (shippingMethodId == konbiniPickupId) return konbiniPickupDays;
        if (shippingMethodId == dropoffId)       return dropoffDays;
        return homeDeliveryDays;
    }
}
