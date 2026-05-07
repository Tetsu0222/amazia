package com.example.delivery.service;

import com.example.delivery.dto.DeliveryResponse;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 配送一覧取得 Service。
 *
 * <p>本フェーズではフィルタリング（ステータス・期間・追跡番号）は Controller 側で
 * クエリパラメータを受け取って Repository の派生メソッドを呼ぶ最小実装に留める。
 * 必要に応じて Specification API への拡張は phase18 以降で検討。
 */
@Service
public class ListDeliveryService {

    private final DeliveryRepository deliveryRepository;

    public ListDeliveryService(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> list(Long shippingStatusId) {
        List<Delivery> entities = (shippingStatusId == null)
                ? deliveryRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))
                : deliveryRepository.findByShippingStatusId(shippingStatusId);
        return entities.stream().map(DeliveryResponse::new).toList();
    }
}
