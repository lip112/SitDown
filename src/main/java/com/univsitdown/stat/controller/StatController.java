package com.univsitdown.stat.controller;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
import com.univsitdown.global.security.CurrentUser;
import com.univsitdown.global.security.UserPrincipal;
import com.univsitdown.stat.dto.StatResponse;
import com.univsitdown.stat.service.StatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;

    @GetMapping("/me")
    public StatResponse getMyStat(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @CurrentUser UserPrincipal principal) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.STAT_INVALID_PERIOD);
        }
        return statService.getStat(principal.userId(), parseDate(from), parseDate(to));
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.STAT_INVALID_PERIOD);
        }
    }
}
