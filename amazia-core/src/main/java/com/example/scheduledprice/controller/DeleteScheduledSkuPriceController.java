package com.example.scheduledprice.controller;

import com.example.scheduledprice.service.DeleteScheduledSkuPriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * フェーズ17 Step 5.5-1e（設計書 §13.5.1）：予約価格 削除 API。
 *
 * <p>{@code DELETE /api/skus/{id}/scheduled-price}：{@code is_pending = TRUE} を物理削除。
 * 削除対象が無ければ {@code 204 No Content}。削除した場合も {@code 204 No Content} を返す
 * （クライアントは「予約変更が消えた」状態だけを認識すればよい）。
 */
@RestController
@RequestMapping("/api")
public class DeleteScheduledSkuPriceController {

    private final DeleteScheduledSkuPriceService service;

    public DeleteScheduledSkuPriceController(DeleteScheduledSkuPriceService service) {
        this.service = service;
    }

    @DeleteMapping("/skus/{id}/scheduled-price")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
