package com.univsitdown.auth.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class ExpiredRefreshTokenException extends BusinessException {
    public ExpiredRefreshTokenException() {
        super(ErrorCode.EXPIRED_REFRESH_TOKEN);
    }
}
