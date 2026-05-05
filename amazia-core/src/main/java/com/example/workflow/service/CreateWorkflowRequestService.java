package com.example.workflow.service;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.workflow.config.WorkflowStatus;
import com.example.workflow.config.WorkflowStepDefinition;
import com.example.workflow.dto.CreateWorkflowRequest;
import com.example.workflow.dto.WorkflowResponse;
import com.example.workflow.entity.WorkflowRequest;
import com.example.workflow.entity.WorkflowRequestDetail;
import com.example.workflow.repository.WorkflowRequestDetailRepository;
import com.example.workflow.repository.WorkflowRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CreateWorkflowRequestService {

    private final WorkflowRequestRepository requestRepository;
    private final WorkflowRequestDetailRepository detailRepository;
    private final UserRepository userRepository;
    private final WorkflowStepDefinition stepDefinition;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CreateWorkflowRequestService(WorkflowRequestRepository requestRepository,
                                        WorkflowRequestDetailRepository detailRepository,
                                        UserRepository userRepository,
                                        WorkflowStepDefinition stepDefinition) {
        this.requestRepository = requestRepository;
        this.detailRepository  = detailRepository;
        this.userRepository    = userRepository;
        this.stepDefinition    = stepDefinition;
    }

    @Transactional
    public WorkflowResponse create(CreateWorkflowRequest dto, Long requesterUserId) {
        if (!stepDefinition.isSupportedTargetType(dto.getTargetType())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Unsupported target_type: " + dto.getTargetType());
        }
        if (requesterUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "requester user id required");
        }

        // 重複申請ガード（pending が既存 → 409）
        if (requestRepository.existsByTargetTypeAndTargetIdAndStatus(
                dto.getTargetType(), dto.getTargetId(), WorkflowStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A pending workflow already exists for this target");
        }

        WorkflowRequest req = new WorkflowRequest();
        req.setTargetType(dto.getTargetType());
        req.setTargetId(dto.getTargetId());
        req.setRequestedBy(requesterUserId);
        req.setStatus(WorkflowStatus.PENDING);
        req.setPayload(serializePayload(dto));
        requestRepository.save(req);

        List<WorkflowStepDefinition.StepDef> steps = stepDefinition.getSteps(dto.getTargetType());
        Map<Integer, Long> destMap = dto.getDestinationUserIds() == null
            ? Map.of() : dto.getDestinationUserIds();

        List<WorkflowRequestDetail> created = new ArrayList<>();
        for (WorkflowStepDefinition.StepDef step : steps) {
            String initialStatus = (step.stepNumber() == 1) ? WorkflowStatus.PENDING : WorkflowStatus.WAITING;
            for (WorkflowStepDefinition.StepTarget target : step.targets()) {
                WorkflowRequestDetail d = new WorkflowRequestDetail();
                d.setWorkflowRequestsId(req.getId());
                d.setStepNumber(step.stepNumber());
                d.setTargetRole(target.role());
                d.setStatus(initialStatus);

                Long destUserId = destMap.get(step.stepNumber());
                if (destUserId != null) {
                    d.setDestinationUserId(destUserId);
                    userRepository.findById(destUserId)
                        .ifPresent(u -> d.setDestinationName(u.getName()));
                }
                detailRepository.save(d);
                created.add(d);
            }
        }

        return WorkflowResponse.from(req, created);
    }

    private String serializePayload(CreateWorkflowRequest dto) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("target_type", dto.getTargetType());
            map.put("target_id",   dto.getTargetId());
            map.put("fields",      dto.getFields());
            if (dto.getMeta() != null) map.put("meta", dto.getMeta());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Failed to serialize payload");
        }
    }
}
