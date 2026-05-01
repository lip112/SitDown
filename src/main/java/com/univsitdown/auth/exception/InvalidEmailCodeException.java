package com.univsitdown.auth.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class InvalidEmailCodeException extends BusinessException {
    public InvalidEmailCodeException() {
        super(ErrorCode.INVALID_EMAIL_CODE);
    }
}
