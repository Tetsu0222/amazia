package com.example.delivery.controller;

import com.example.delivery.dto.DeliveryResponse;
import com.example.delivery.dto.UpdateScheduledDateRequest;
import com.example.delivery.entity.Delivery;
import com.example.delivery.service.UpdateScheduledDateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UpdateScheduledDateController {

    private final UpdateScheduledDateService service;

    public UpdateScheduledDateController(UpdateScheduledDateService service) {
        this.service = service;
    }

    @PatchMapping("/deliveries/{id}/scheduled-date")
    public ResponseEntity<DeliveryResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateScheduledDateRequest request,
                                                   @RequestHeader("X-User-Id") Long userId) {
        Delivery updated = service.update(id, request.getScheduledDate(),
                request.getReason(), userId);
        return ResponseEntity.ok(new DeliveryResponse(updated));
    }
}
