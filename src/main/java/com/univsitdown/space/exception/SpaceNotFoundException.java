package com.univsitdown.space.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

// 공간 ID로 조회 시 존재하지 않을 때 발생. HTTP 404 / 에러코드 SPACE-001 반환.
public class SpaceNotFoundException extends BusinessException {
    public SpaceNotFoundException() {
        super(ErrorCode.SPACE_NOT_FOUND);
    }
}
