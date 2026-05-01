package com.univsitdown.auth.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class EmailSendRateLimitException extends BusinessException {
    public EmailSendRateLimitException() {
        super(ErrorCode.EMAIL_SEND_RATE_LIMIT);
    }
}
