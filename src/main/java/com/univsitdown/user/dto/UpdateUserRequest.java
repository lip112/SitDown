package com.univsitdown.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 2, max = 20, message = "이름은 2자 이상 20자 이하여야 합니다.")
        String name,

        @Pattern(
                regexp = "^\\d{3}-\\d{4}-\\d{4}$",
                message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)"
        )
        String phone,

        @Size(max = 100, message = "소속은 100자 이하여야 합니다.")
        String affiliation
) {}
