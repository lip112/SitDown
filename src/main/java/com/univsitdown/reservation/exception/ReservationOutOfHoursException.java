package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationOutOfHoursException extends BusinessException {
    public ReservationOutOfHoursException() { super(ErrorCode.RESERVATION_OUT_OF_HOURS); }
}
