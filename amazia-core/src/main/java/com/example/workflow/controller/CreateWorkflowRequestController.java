package com.example.workflow.controller;

import com.example.workflow.dto.CreateWorkflowRequest;
import com.example.workflow.dto.WorkflowResponse;
import com.example.workflow.service.CreateWorkflowRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CreateWorkflowRequestController {

    private final CreateWorkflowRequestService service;

    public CreateWorkflowRequestController(CreateWorkflowRequestService service) {
        this.service = service;
    }

    @PostMapping("/workflows")
    public ResponseEntity<WorkflowResponse> create(@Valid @RequestBody CreateWorkflowRequest body,
                                                   @RequestHeader("X-User-Id") Long userId) {
        WorkflowResponse res = service.create(body, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }
}
