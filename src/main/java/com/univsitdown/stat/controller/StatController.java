package com.univsitdown.stat.controller;

import com.univsitdown.global.security.CurrentUser;
import com.univsitdown.global.security.UserPrincipal;
import com.univsitdown.stat.dto.StatResponse;
import com.univsitdown.stat.service.StatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;

    @GetMapping("/me")
    public StatResponse getMyStat(
            @RequestParam(defaultValue = "WEEKLY") String period,
            @CurrentUser UserPrincipal principal) {
        return statService.getStat(principal.userId(), period);
    }
}
