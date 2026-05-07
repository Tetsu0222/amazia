package com.example.delivery.service;

import com.example.delivery.dto.DeliveryResponse;
import com.example.delivery.repository.DeliveryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GetDeliveryService {

    private final DeliveryRepository deliveryRepository;

    public GetDeliveryService(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(readOnly = true)
    public DeliveryResponse get(Long id) {
        return deliveryRepository.findById(id)
                .map(DeliveryResponse::new)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "delivery not found"));
    }
}
