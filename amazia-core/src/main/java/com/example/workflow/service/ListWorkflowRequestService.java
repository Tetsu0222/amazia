package com.example.workflow.service;

import com.example.workflow.dto.WorkflowResponse;
import com.example.workflow.entity.WorkflowRequest;
import com.example.workflow.entity.WorkflowRequestDetail;
import com.example.workflow.repository.WorkflowRequestDetailRepository;
import com.example.workflow.repository.WorkflowRequestRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListWorkflowRequestService {

    private final WorkflowRequestRepository requestRepository;
    private final WorkflowRequestDetailRepository detailRepository;

    public ListWorkflowRequestService(WorkflowRequestRepository requestRepository,
                                      WorkflowRequestDetailRepository detailRepository) {
        this.requestRepository = requestRepository;
        this.detailRepository  = detailRepository;
    }

    public List<WorkflowResponse> list(String status) {
        List<WorkflowRequest> requests = (status == null || status.isBlank())
            ? requestRepository.findAllByOrderByCreatedAtDesc()
            : requestRepository.findByStatusOrderByCreatedAtDesc(status);

        return requests.stream()
            .map(req -> {
                List<WorkflowRequestDetail> details =
                    detailRepository.findByWorkflowRequestsIdOrderByStepNumberAscIdAsc(req.getId());
                return WorkflowResponse.from(req, details);
            })
            .toList();
    }
}
