package com.example.inbound.service;

import com.example.inbound.dto.InboundResponse;
import com.example.inbound.entity.Inbound;
import com.example.inbound.repository.InboundRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListInboundService {

    private final InboundRepository inboundRepository;

    public ListInboundService(InboundRepository inboundRepository) {
        this.inboundRepository = inboundRepository;
    }

    @Transactional(readOnly = true)
    public List<InboundResponse> list(Long productId) {
        List<Inbound> entities = (productId == null)
                ? inboundRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))
                : inboundRepository.findByProductIdOrderByInboundedAtDesc(productId);
        return entities.stream().map(InboundResponse::new).toList();
    }
}
