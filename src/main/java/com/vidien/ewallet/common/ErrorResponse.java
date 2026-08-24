package com.vidien.ewallet.common;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(Instant timestamp, int status, String code, String message, String path,
        List<FieldError> fieldErrors) {
    /**
     * Một field trượt một luật. Là DANH SÁCH chứ không phải map {field: message}, vì một field có
     * thể trượt nhiều luật cùng lúc.
     */
    public record FieldError(String field, String message) {
    }

    /** Lỗi không gắn với field nào: JSON hỏng, lỗi hệ thống. */
    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, code, message, path, null);
    }

    /** Lỗi validation - kèm danh sách field đã trượt. */
    public static ErrorResponse of(int status, String code, String message, String path,
            List<FieldError> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, code, message, path, fieldErrors);
    }
}
