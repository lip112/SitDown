package com.univsitdown.auth.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class AccountLockedException extends BusinessException {
    public AccountLockedException() {
        super(ErrorCode.ACCOUNT_LOCKED);
    }
}
