package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationNotFoundException extends BusinessException {
    public ReservationNotFoundException() { super(ErrorCode.RESERVATION_NOT_FOUND); }
}
