package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class SeatAlreadyReservedException extends BusinessException {
    public SeatAlreadyReservedException() { super(ErrorCode.SEAT_ALREADY_RESERVED); }
}
