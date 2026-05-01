package com.univsitdown.space.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class SeatGridSizeExceededException extends BusinessException {
    public SeatGridSizeExceededException() { super(ErrorCode.SEAT_GRID_SIZE_EXCEEDED); }
}
