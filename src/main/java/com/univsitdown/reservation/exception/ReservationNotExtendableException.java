package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationNotExtendableException extends BusinessException {
    public ReservationNotExtendableException() { super(ErrorCode.RESERVATION_NOT_EXTENDABLE); }
}
