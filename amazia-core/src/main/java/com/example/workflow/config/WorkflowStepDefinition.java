package com.example.workflow.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ワークフロー種別ごとのステップ定義を保持する。
 * 規約 3-2「ワークフローのステップ定義は条件付きで config 化してよい」に従い、
 * 単純な定数なため Java で集中管理する。
 */
@Component
public class WorkflowStepDefinition {

    public static final String TARGET_PRODUCT = "product";
    public static final String TARGET_PRICE   = "price";
    public static final String TARGET_STOCK   = "stock";

    public static final String ROLE_USER            = "user";
    public static final String ROLE_SUPERVISOR      = "supervisor";
    public static final String ROLE_ADMIN           = "admin";
    public static final String ROLE_SENIOR_ADMIN    = "senior_admin";
    public static final String ROLE_ETERNAL_ADVISOR = "eternal_advisor";

    public record StepTarget(String role) {}
    public record StepDef(int stepNumber, boolean parallel, List<StepTarget> targets) {}

    private static final Map<String, List<StepDef>> DEFINITIONS = Map.of(
        TARGET_PRODUCT, List.of(
            new StepDef(1, false, List.of(new StepTarget(ROLE_ADMIN)))
        ),
        TARGET_STOCK, List.of(
            new StepDef(1, true,  List.of(new StepTarget(ROLE_SUPERVISOR), new StepTarget(ROLE_ADMIN))),
            new StepDef(2, false, List.of(new StepTarget(ROLE_SENIOR_ADMIN)))
        ),
        TARGET_PRICE, List.of(
            new StepDef(1, false, List.of(new StepTarget(ROLE_SUPERVISOR))),
            new StepDef(2, false, List.of(new StepTarget(ROLE_ADMIN)))
        )
    );

    public List<StepDef> getSteps(String targetType) {
        List<StepDef> steps = DEFINITIONS.get(targetType);
        if (steps == null) {
            throw new IllegalArgumentException("Unknown workflow target_type: " + targetType);
        }
        return steps;
    }

    public boolean isSupportedTargetType(String targetType) {
        return DEFINITIONS.containsKey(targetType);
    }
}
