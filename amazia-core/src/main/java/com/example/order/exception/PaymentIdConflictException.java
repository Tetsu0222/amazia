package com.example.order.exception;

/**
 * payment_id UNIQUE 違反かつ user_id / sku_id / quantity / amount が一致しないケースで投げる例外。
 *
 * 設計書 r4 / S14-5 のセキュリティ要件：
 *   payment_id の重複だけで冪等扱いすると、決済 ID が漏れた場合に他人の購入詐取が可能になる。
 *   そのため payment_id が重複していても他項目が一致しない場合は **例外を投げて拒否** し、
 *   要監視レベルのエラーログとして記録する。
 */
public class PaymentIdConflictException extends RuntimeException {

    public PaymentIdConflictException(String message) {
        super(message);
    }
}
