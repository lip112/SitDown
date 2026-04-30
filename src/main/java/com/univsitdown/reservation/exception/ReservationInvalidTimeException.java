package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationInvalidTimeException extends BusinessException {
    public ReservationInvalidTimeException() { super(ErrorCode.RESERVATION_INVALID_TIME); }
}
