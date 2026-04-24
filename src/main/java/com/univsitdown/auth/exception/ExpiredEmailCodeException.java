package com.univsitdown.auth.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class ExpiredEmailCodeException extends BusinessException {
    public ExpiredEmailCodeException() {
        super(ErrorCode.EXPIRED_EMAIL_CODE);
    }
}
