package com.univsitdown.space.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class SeatUnavailableException extends BusinessException {
    public SeatUnavailableException() { super(ErrorCode.SEAT_UNAVAILABLE); }
}
