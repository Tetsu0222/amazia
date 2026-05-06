package com.example.market.customer.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * フェーズ13 ステップ3 の Filter（Auth / CSRF）の挙動を検証するためのテスト専用 Controller。
 *
 * 本物の顧客 API はステップ4 で実装するため、この時点では Filter の単体動作を観測できる
 * エンドポイントが他にない。テストプロファイル下のみ起動するダミー。
 */
@RestController
@Profile("test")
public class MarketFilterTestController {

    /**
     * 認証不要・CSRF 検証対象外（GET）。Filter が Cookie を受けて customerId 属性を立てたかを返す。
     */
    @GetMapping("/api/customer/test/whoami")
    public Map<String, Object> whoami(HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        Object customerId = request.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        body.put("customerId", customerId);
        body.put("authenticated", customerId != null);
        return body;
    }

    /**
     * CSRF 検証対象（POST、保護パス、除外リスト外）。通れば 200 を返す。
     */
    @PostMapping("/api/customer/test/protected")
    public ResponseEntity<Map<String, Object>> protectedAction() {
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
