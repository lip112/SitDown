package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationMaxDurationExceededException extends BusinessException {
    public ReservationMaxDurationExceededException() { super(ErrorCode.RESERVATION_MAX_DURATION_EXCEEDED); }
}
