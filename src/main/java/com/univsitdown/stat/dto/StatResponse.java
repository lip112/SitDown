package com.univsitdown.stat.dto;

import java.util.List;

public record StatResponse(
        String period,
        String from,
        String to,
        long totalMinutes,
        long comparedToPreviousMinutes,
        List<DailyItem> daily,
        List<TopSpaceItem> topSpaces
) {
    public record DailyItem(String date, long minutes) {}
    public record TopSpaceItem(String spaceId, String spaceName, long minutes) {}
}
