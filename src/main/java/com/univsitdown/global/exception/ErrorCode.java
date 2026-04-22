package com.univsitdown.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // AUTH
    INVALID_EMAIL_FORMAT("AUTH-101", HttpStatus.BAD_REQUEST, "유효한 이메일을 입력해 주세요."),
    INVALID_PASSWORD_POLICY("AUTH-102", HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상, 영문/숫자/특수문자를 포함해야 합니다."),
    EMAIL_NOT_VERIFIED("AUTH-103", HttpStatus.BAD_REQUEST, "이메일 인증을 먼저 완료해 주세요."),
    EMAIL_DUPLICATED("AUTH-104", HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    EMAIL_SEND_RATE_LIMIT("AUTH-105", HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요."),
    INVALID_EMAIL_CODE("AUTH-111", HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다."),
    EXPIRED_EMAIL_CODE("AUTH-112", HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다. 재발송해 주세요."),
    INVALID_CREDENTIALS("AUTH-201", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_LOCKED("AUTH-202", HttpStatus.LOCKED, "로그인 5회 실패로 계정이 잠겼습니다."),
    EXPIRED_REFRESH_TOKEN("AUTH-211", HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    INVALID_REFRESH_TOKEN("AUTH-212", HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),

    // USER
    USER_NOT_FOUND("USER-001", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // SPACE
    SPACE_NOT_FOUND("SPACE-001", HttpStatus.NOT_FOUND, "공간을 찾을 수 없습니다."),

    // SEAT
    SEAT_NOT_FOUND("SEAT-001", HttpStatus.NOT_FOUND, "좌석을 찾을 수 없습니다."),
    SEAT_UNAVAILABLE("SEAT-002", HttpStatus.CONFLICT, "현재 이용할 수 없는 좌석입니다."),

    // RESERVATION
    RESERVATION_OUT_OF_HOURS("RSV-001", HttpStatus.BAD_REQUEST, "운영 시간 외에는 예약할 수 없습니다."),
    RESERVATION_MAX_DURATION_EXCEEDED("RSV-002", HttpStatus.BAD_REQUEST, "최대 이용 시간은 4시간입니다."),
    RESERVATION_INVALID_TIME("RSV-003", HttpStatus.BAD_REQUEST, "시작 시간이 종료 시간보다 빨라야 합니다."),
    SEAT_ALREADY_RESERVED("RSV-004", HttpStatus.CONFLICT, "이미 해당 시간대에 예약된 좌석입니다."),
    USER_RESERVATION_LIMIT("RSV-005", HttpStatus.CONFLICT, "진행 중 또는 예정 예약이 이미 존재합니다."),
    RESERVATION_NOT_EXTENDABLE("RSV-011", HttpStatus.BAD_REQUEST, "진행 중인 예약만 연장할 수 있습니다."),
    RESERVATION_EXTEND_CONFLICT("RSV-012", HttpStatus.CONFLICT, "다음 예약과 겹쳐 연장할 수 없습니다."),
    RESERVATION_MAX_EXTEND_EXCEEDED("RSV-013", HttpStatus.BAD_REQUEST, "더 이상 연장할 수 없습니다."),
    RESERVATION_NOT_OWNER("RSV-021", HttpStatus.FORBIDDEN, "본인의 예약만 취소할 수 있습니다."),
    RESERVATION_ALREADY_ENDED("RSV-022", HttpStatus.BAD_REQUEST, "이미 종료된 예약입니다."),

    // ADMIN
    SEAT_GRID_SIZE_EXCEEDED("ADMIN-001", HttpStatus.BAD_REQUEST, "행과 열은 각각 최대 20까지 허용됩니다."),
    SEAT_ALREADY_EXISTS("ADMIN-002", HttpStatus.CONFLICT, "이미 좌석이 존재합니다."),

    // COMMON
    INTERNAL_SERVER_ERROR("COMMON-001", HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요."),
    SERVICE_MAINTENANCE("COMMON-002", HttpStatus.SERVICE_UNAVAILABLE, "서비스 점검 중입니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
