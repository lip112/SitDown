package com.univsitdown.space.dto;

import java.util.List;

public record CongestionPredictionResponse(
        String spaceId,
        String date,
        List<HourlyItem> hourly
) {
    public record HourlyItem(int hour, double occupancyRate, String level) {}

    public static String toLevel(double rate) {
        if (rate < 0.40) return "LOW";
        if (rate < 0.75) return "NORMAL";
        return "HIGH";
    }
}
