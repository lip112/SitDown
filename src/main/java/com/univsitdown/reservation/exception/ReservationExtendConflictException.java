package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationExtendConflictException extends BusinessException {
    public ReservationExtendConflictException() { super(ErrorCode.RESERVATION_EXTEND_CONFLICT); }
}
