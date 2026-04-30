package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationNotOwnerException extends BusinessException {
    public ReservationNotOwnerException() { super(ErrorCode.RESERVATION_NOT_OWNER); }
}
