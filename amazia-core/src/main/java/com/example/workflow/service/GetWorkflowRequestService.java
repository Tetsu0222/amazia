package com.example.workflow.service;

import com.example.workflow.dto.WorkflowResponse;
import com.example.workflow.entity.WorkflowRequest;
import com.example.workflow.entity.WorkflowRequestDetail;
import com.example.workflow.repository.WorkflowRequestDetailRepository;
import com.example.workflow.repository.WorkflowRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class GetWorkflowRequestService {

    private final WorkflowRequestRepository requestRepository;
    private final WorkflowRequestDetailRepository detailRepository;

    public GetWorkflowRequestService(WorkflowRequestRepository requestRepository,
                                     WorkflowRequestDetailRepository detailRepository) {
        this.requestRepository = requestRepository;
        this.detailRepository  = detailRepository;
    }

    public WorkflowResponse get(Long id) {
        WorkflowRequest req = requestRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));
        List<WorkflowRequestDetail> details =
            detailRepository.findByWorkflowRequestsIdOrderByStepNumberAscIdAsc(req.getId());
        return WorkflowResponse.from(req, details);
    }
}
