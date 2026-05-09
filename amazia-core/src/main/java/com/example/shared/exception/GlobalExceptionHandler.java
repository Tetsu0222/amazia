package com.example.shared.exception;

import com.example.inquiry.exception.ForbiddenInquiryAccessException;
import com.example.inquiry.exception.IllegalInquiryStatusTransitionException;
import com.example.inquiry.exception.InquiryNotFoundException;
import com.example.inquiry.exception.InquiryValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
                .toList();

        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        body.put("error", "Unprocessable Entity");
        body.put("errors", fieldErrors);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", ex.getStatusCode().value());
        body.put("error", "Unprocessable Entity");
        body.put("message", ex.getReason());

        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    /** フェーズ18: 不正なステータス遷移は HTTP 400（設計書 §5.2 / I-6）。 */
    @ExceptionHandler(IllegalInquiryStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalInquiryStatusTransition(
            IllegalInquiryStatusTransitionException ex) {
        return errorBody(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    /** フェーズ18: 他顧客の問い合わせ／対象資源への不正アクセスは HTTP 403（設計書 §5.4 / RV-5）。 */
    @ExceptionHandler(ForbiddenInquiryAccessException.class)
    public ResponseEntity<Map<String, Object>> handleForbiddenInquiryAccess(
            ForbiddenInquiryAccessException ex) {
        return errorBody(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    /** フェーズ18: 問い合わせ／関連エンティティが見つからないときは HTTP 404（設計書 §5.5）。 */
    @ExceptionHandler(InquiryNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleInquiryNotFound(
            InquiryNotFoundException ex) {
        return errorBody(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    /** フェーズ18: 問い合わせドメインの入力バリデーション違反は HTTP 400（設計書 §5.3）。 */
    @ExceptionHandler(InquiryValidationException.class)
    public ResponseEntity<Map<String, Object>> handleInquiryValidation(InquiryValidationException ex) {
        return errorBody(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> errorBody(HttpStatus status, String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

}
