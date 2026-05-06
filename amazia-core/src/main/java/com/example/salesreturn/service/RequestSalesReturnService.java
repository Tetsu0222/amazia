package com.example.salesreturn.service;

import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.salesreturn.dto.RequestSalesReturnRequest;
import com.example.salesreturn.entity.SalesReturn;
import com.example.salesreturn.repository.SalesReturnRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 返品申請 Service（B-5-1 / 設計書 r4 phase14 §返品申請）。
 *
 * 会員自身の sales に対して返品申請（status=REQUESTED）を行う。
 *
 * 検証順：
 *   1. sales が存在する
 *   2. 本人所有である（sales.user_id == customerId）
 *   3. 配送ステータスが DELIVERED である
 *   4. quantity が sales.quantity 以下である
 *   5. 同 sales_id に未終了の sales_return が存在しない（REQUESTED / APPROVED / REFUNDED）
 *
 * REJECTED は終了扱いで、別案件として再申請可能。
 */
@Service
public class RequestSalesReturnService {

    private static final List<String> ACTIVE_STATUSES = List.of("REQUESTED", "APPROVED", "REFUNDED");

    private final SalesRepository salesRepository;
    private final SalesReturnRepository salesReturnRepository;
    private final long deliveredStatusId;

    public RequestSalesReturnService(SalesRepository salesRepository,
                                     SalesReturnRepository salesReturnRepository,
                                     @Value("${amazia.sales.shipping-statuses.delivered-id}") long deliveredStatusId) {
        this.salesRepository = salesRepository;
        this.salesReturnRepository = salesReturnRepository;
        this.deliveredStatusId = deliveredStatusId;
    }

    @Transactional
    public SalesReturn request(Long customerId, RequestSalesReturnRequest req) {
        Sales sales = salesRepository.findById(req.getSalesId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sales not found"));

        if (!sales.getUserId().equals(customerId)) {
            // 本人以外の sales に対する申請は存在自体を隠す
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "sales not found");
        }
        if (sales.getShippingStatusId() != deliveredStatusId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "sales is not delivered yet");
        }
        if (req.getQuantity() > sales.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity exceeds purchased quantity");
        }
        salesReturnRepository.findFirstBySalesIdAndStatusIn(sales.getId(), ACTIVE_STATUSES)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "sales_return already exists in status " + existing.getStatus());
                });

        SalesReturn ret = new SalesReturn();
        ret.setSalesId(sales.getId());
        ret.setStatus("REQUESTED");
        ret.setQuantity(req.getQuantity());
        ret.setReason(req.getReason());
        ret.setNotifiedUser(false);
        ret.setNotifiedAdmin(false);
        return salesReturnRepository.save(ret);
    }
}
