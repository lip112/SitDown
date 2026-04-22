package com.univsitdown.global.response;

import com.univsitdown.global.exception.ErrorCode;

import java.time.Instant;

public record ErrorResponse(
        String code,
        String message,
        String timestamp,
        String traceId,
        String path
) {
    public static ErrorResponse of(ErrorCode errorCode, String traceId, String path) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                Instant.now().toString(),
                traceId,
                path
        );
    }
}
