package com.example.workflow.config;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * ワークフロー権限判定用のロール階層ヘルパ。
 */
@Component
public class WorkflowRoleHierarchy {

    public static final String ROLE_ETERNAL_ADVISOR = WorkflowStepDefinition.ROLE_ETERNAL_ADVISOR;

    private static final Set<String> APPROVER_ROLES = Set.of(
        WorkflowStepDefinition.ROLE_SUPERVISOR,
        WorkflowStepDefinition.ROLE_ADMIN,
        WorkflowStepDefinition.ROLE_SENIOR_ADMIN,
        WorkflowStepDefinition.ROLE_ETERNAL_ADVISOR
    );

    public boolean isEternalAdvisor(String role) {
        return ROLE_ETERNAL_ADVISOR.equals(role);
    }

    public boolean canApprove(String role) {
        return APPROVER_ROLES.contains(role);
    }
}
