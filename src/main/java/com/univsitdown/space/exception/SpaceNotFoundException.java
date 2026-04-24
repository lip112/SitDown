package com.univsitdown.space.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class SpaceNotFoundException extends BusinessException {
    public SpaceNotFoundException() {
        super(ErrorCode.SPACE_NOT_FOUND);
    }
}
