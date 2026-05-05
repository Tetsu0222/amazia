package com.example.workflow.controller;

import com.example.workflow.dto.WorkflowResponse;
import com.example.workflow.service.GetWorkflowRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GetWorkflowRequestController {

    private final GetWorkflowRequestService service;

    public GetWorkflowRequestController(GetWorkflowRequestService service) {
        this.service = service;
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<WorkflowResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }
}
