package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationAlreadyEndedException extends BusinessException {
    public ReservationAlreadyEndedException() { super(ErrorCode.RESERVATION_ALREADY_ENDED); }
}
