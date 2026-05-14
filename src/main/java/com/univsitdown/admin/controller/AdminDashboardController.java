package com.univsitdown.admin.controller;

import com.univsitdown.admin.dto.AdminDashboardResponse;
import com.univsitdown.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public AdminDashboardResponse getDashboard() {
        return adminDashboardService.getDashboard();
    }
}
