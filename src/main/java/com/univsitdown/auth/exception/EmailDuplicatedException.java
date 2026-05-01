package com.univsitdown.auth.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class EmailDuplicatedException extends BusinessException {
    public EmailDuplicatedException() {
        super(ErrorCode.EMAIL_DUPLICATED);
    }
}
