package com.example.workflow.controller;

import com.example.workflow.dto.WorkflowResponse;
import com.example.workflow.service.CancelWorkflowRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CancelWorkflowRequestController {

    private final CancelWorkflowRequestService service;

    public CancelWorkflowRequestController(CancelWorkflowRequestService service) {
        this.service = service;
    }

    @PostMapping("/workflows/{id}/cancel")
    public ResponseEntity<WorkflowResponse> cancel(@PathVariable Long id,
                                                   @RequestHeader("X-User-Id") Long userId,
                                                   @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(service.cancel(id, userId, role));
    }
}
