package com.univsitdown.space.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class SeatNotFoundException extends BusinessException {
    public SeatNotFoundException() { super(ErrorCode.SEAT_NOT_FOUND); }
}
