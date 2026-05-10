package com.example.delivery.service;

import com.example.delivery.dto.ShippingLeadTimeResponse;
import com.example.delivery.entity.ShippingLeadTime;
import com.example.delivery.repository.ShippingLeadTimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListShippingLeadTimeService {

    private final ShippingLeadTimeRepository repository;

    public ListShippingLeadTimeService(ShippingLeadTimeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ShippingLeadTimeResponse> list(Long shippingMethodId) {
        List<ShippingLeadTime> entities = (shippingMethodId == null)
                ? repository.findAllByOrderByShippingMethodIdAscIdAsc()
                : repository.findByShippingMethodIdOrderByIdAsc(shippingMethodId);
        return entities.stream().map(ShippingLeadTimeResponse::new).toList();
    }
}
