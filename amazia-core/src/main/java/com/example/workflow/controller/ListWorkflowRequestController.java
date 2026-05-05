package com.example.workflow.controller;

import com.example.workflow.dto.WorkflowResponse;
import com.example.workflow.service.ListWorkflowRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ListWorkflowRequestController {

    private final ListWorkflowRequestService service;

    public ListWorkflowRequestController(ListWorkflowRequestService service) {
        this.service = service;
    }

    @GetMapping("/workflows")
    public ResponseEntity<List<WorkflowResponse>> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.list(status));
    }
}
