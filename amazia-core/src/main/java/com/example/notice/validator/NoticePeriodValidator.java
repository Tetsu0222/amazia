package com.example.notice.validator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * お知らせ公開期間の検証（フェーズ19 r2 / 設計書 R19-6 / 二重防御）。
 *
 * <p>本番 MySQL では schema.sql の {@code CHECK (publish_start <= publish_end)} で物理担保するが、
 * H2 テスト環境では DDL 自動生成のため CHECK が無い。本 Validator は Service 層で
 * 二重防御を行い、422 を返す（H2 / 本番双方で同じ動作）。
 */
@Component
public class NoticePeriodValidator {

    /**
     * publish_start <= publish_end であることを検証する。
     *
     * @throws ResponseStatusException 422 Unprocessable Entity
     */
    public void validate(LocalDateTime publishStart, LocalDateTime publishEnd) {
        if (publishStart == null || publishEnd == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "publish_start and publish_end are required");
        }
        if (publishStart.isAfter(publishEnd)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "publish_start must be on or before publish_end");
        }
    }
}
