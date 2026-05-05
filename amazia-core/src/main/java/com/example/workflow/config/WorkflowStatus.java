package com.example.workflow.config;

public final class WorkflowStatus {

    private WorkflowStatus() {}

    // 親ステータス
    public static final String PENDING   = "pending";
    public static final String APPROVED  = "approved";
    public static final String REJECTED  = "rejected";
    public static final String CANCELED  = "canceled";

    // 詳細ステータス（追加）
    public static final String WAITING   = "waiting";
}
