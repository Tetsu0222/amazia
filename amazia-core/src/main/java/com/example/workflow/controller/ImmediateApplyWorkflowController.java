package com.example.workflow.controller;

import com.example.workflow.dto.CreateWorkflowRequest;
import com.example.workflow.service.ImmediateApplyWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ImmediateApplyWorkflowController {

    private final ImmediateApplyWorkflowService service;

    public ImmediateApplyWorkflowController(ImmediateApplyWorkflowService service) {
        this.service = service;
    }

    @PostMapping("/workflows/immediate-apply")
    public ResponseEntity<Map<String, String>> apply(@Valid @RequestBody CreateWorkflowRequest body,
                                                     @RequestHeader("X-User-Role") String role) {
        service.apply(body, role);
        return ResponseEntity.ok(Map.of("status", "applied"));
    }
}
