package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationMaxExtendExceededException extends BusinessException {
    public ReservationMaxExtendExceededException() { super(ErrorCode.RESERVATION_MAX_EXTEND_EXCEEDED); }
}
