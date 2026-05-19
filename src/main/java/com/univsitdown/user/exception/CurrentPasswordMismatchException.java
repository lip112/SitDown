package com.univsitdown.user.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class CurrentPasswordMismatchException extends BusinessException {
    public CurrentPasswordMismatchException() {
        super(ErrorCode.CURRENT_PASSWORD_MISMATCH);
    }
}
