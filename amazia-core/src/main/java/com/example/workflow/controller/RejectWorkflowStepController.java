package com.example.workflow.controller;

import com.example.workflow.dto.WorkflowResponse;
import com.example.workflow.service.RejectWorkflowStepService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RejectWorkflowStepController {

    private final RejectWorkflowStepService service;

    public RejectWorkflowStepController(RejectWorkflowStepService service) {
        this.service = service;
    }

    @PostMapping("/workflows/{id}/steps/{stepNumber}/reject")
    public ResponseEntity<WorkflowResponse> reject(@PathVariable Long id,
                                                   @PathVariable Integer stepNumber,
                                                   @RequestHeader("X-User-Id") Long userId,
                                                   @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(service.reject(id, stepNumber, userId, role));
    }
}
