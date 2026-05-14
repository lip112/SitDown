package com.univsitdown.global.exception;

import com.univsitdown.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException e, HttpServletRequest request) {
        log.warn("[BusinessException] code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ErrorResponse.of(e.getErrorCode(), resolveTraceId(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("[ValidationException] path={}, message={}", request.getRequestURI(), message);
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCode.VALIDATION_FAILED.getCode(),
                message,
                Instant.now().toString(),
                resolveTraceId(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("[HttpMessageNotReadableException] path={}", request.getRequestURI());
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCode.VALIDATION_FAILED.getCode(),
                "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해 주세요.",
                Instant.now().toString(),
                resolveTraceId(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e, HttpServletRequest request) {
        log.warn("[NoResourceFoundException] path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                ErrorCode.VALIDATION_FAILED.getCode(),
                "요청한 경로를 찾을 수 없습니다.",
                Instant.now().toString(),
                resolveTraceId(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("[MethodArgumentTypeMismatchException] param={}, value={}", e.getName(), e.getValue());
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCode.VALIDATION_FAILED.getCode(),
                "경로 변수 '" + e.getName() + "'의 형식이 올바르지 않습니다.",
                Instant.now().toString(),
                resolveTraceId(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error("[UnexpectedException] path={}", request.getRequestURI(), e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, resolveTraceId(), request.getRequestURI()));
    }

    // 16자리 hex traceId. MDC에 이미 있으면 같은 요청의 로그 추적 ID를 재사용한다.
    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
