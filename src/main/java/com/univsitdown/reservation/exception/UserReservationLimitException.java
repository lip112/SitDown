package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class UserReservationLimitException extends BusinessException {
    public UserReservationLimitException() { super(ErrorCode.USER_RESERVATION_LIMIT); }
}
