package com.univsitdown.admin.dto;

public record AdminDashboardResponse(
        long spaceCount,
        long activeReservationCount
) {}
