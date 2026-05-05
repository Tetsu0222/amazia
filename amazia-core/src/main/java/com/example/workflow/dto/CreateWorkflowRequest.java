package com.example.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public class CreateWorkflowRequest {

    @NotBlank
    private String targetType;

    @NotNull
    private Long targetId;

    @NotNull
    private List<FieldDiff> fields;

    private Map<String, Object> meta;

    /** 任意：ステップ毎の destination_user_id 指定（step_number→userId） */
    private Map<Integer, Long> destinationUserIds;

    public static class FieldDiff {
        @NotBlank
        private String field;
        private Object before;
        private Object after;

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public Object getBefore() { return before; }
        public void setBefore(Object before) { this.before = before; }
        public Object getAfter() { return after; }
        public void setAfter(Object after) { this.after = after; }
    }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public List<FieldDiff> getFields() { return fields; }
    public void setFields(List<FieldDiff> fields) { this.fields = fields; }
    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }
    public Map<Integer, Long> getDestinationUserIds() { return destinationUserIds; }
    public void setDestinationUserIds(Map<Integer, Long> destinationUserIds) { this.destinationUserIds = destinationUserIds; }
}
