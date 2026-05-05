package com.example.workflow.service;

import com.example.workflow.config.WorkflowRoleHierarchy;
import com.example.workflow.config.WorkflowStepDefinition;
import com.example.workflow.dto.CreateWorkflowRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * 権限者が「即時反映」ボタンを押したときの処理。
 * ワークフローレコードは作らず、二重検証を経て直接反映する。
 */
@Service
public class ImmediateApplyWorkflowService {

    private final ApplyWorkflowService applyWorkflowService;
    private final WorkflowStepDefinition stepDefinition;
    private final WorkflowRoleHierarchy roleHierarchy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImmediateApplyWorkflowService(ApplyWorkflowService applyWorkflowService,
                                         WorkflowStepDefinition stepDefinition,
                                         WorkflowRoleHierarchy roleHierarchy) {
        this.applyWorkflowService = applyWorkflowService;
        this.stepDefinition       = stepDefinition;
        this.roleHierarchy        = roleHierarchy;
    }

    @Transactional
    public void apply(CreateWorkflowRequest dto, String actorRole) {
        if (!roleHierarchy.canApprove(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Immediate apply requires approver role");
        }
        if (!stepDefinition.isSupportedTargetType(dto.getTargetType())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Unsupported target_type: " + dto.getTargetType());
        }

        try {
            Map<String, Object> map = new HashMap<>();
            map.put("target_type", dto.getTargetType());
            map.put("target_id",   dto.getTargetId());
            map.put("fields",      dto.getFields());
            String json = objectMapper.writeValueAsString(map);
            applyWorkflowService.apply(dto.getTargetType(), dto.getTargetId(), json);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Failed to apply: " + e.getMessage());
        }
    }
}
