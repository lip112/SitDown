package com.univsitdown.auth.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class InvalidRefreshTokenException extends BusinessException {
    public InvalidRefreshTokenException() {
        super(ErrorCode.INVALID_REFRESH_TOKEN);
    }
}
