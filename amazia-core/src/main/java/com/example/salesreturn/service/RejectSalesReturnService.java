package com.example.salesreturn.service;

import com.example.salesreturn.entity.SalesReturn;
import com.example.salesreturn.repository.SalesReturnRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * 返品却下 Service（B-5-2 / 設計書 r4 phase14 §返品却下）。
 *
 * status=REQUESTED の sales_return を REJECTED に遷移させる。
 * 配送ステータスは変更しない（DELIVERED のまま、再申請は B-5-1 で許容）。
 *
 * 遷移ガード：REQUESTED 以外からの遷移は 409 CONFLICT。
 */
@Service
public class RejectSalesReturnService {

    private final SalesReturnRepository salesReturnRepository;

    public RejectSalesReturnService(SalesReturnRepository salesReturnRepository) {
        this.salesReturnRepository = salesReturnRepository;
    }

    @Transactional
    public SalesReturn reject(Long salesReturnId, Long approverUserId) {
        SalesReturn ret = salesReturnRepository.findById(salesReturnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sales_return not found"));

        if (!"REQUESTED".equals(ret.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "sales_return cannot be rejected from status " + ret.getStatus());
        }

        ret.setStatus("REJECTED");
        ret.setApproverId(approverUserId);
        ret.setApprovedAt(LocalDateTime.now());
        return salesReturnRepository.save(ret);
    }
}
