package com.univsitdown.space.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class SeatAlreadyExistsException extends BusinessException {
    public SeatAlreadyExistsException() { super(ErrorCode.SEAT_ALREADY_EXISTS); }
}
