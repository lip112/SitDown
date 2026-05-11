package com.univsitdown.stat.service;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.stat.dto.StatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StatService {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public StatResponse getStat(UUID userId, String period) {
        LocalDate today = LocalDate.now(KST);
        LocalDate[] range     = getRange(period, today);
        LocalDate[] prevRange = getRange(period, today.minus(getPeriodLength(period)));

        LocalDateTime from     = range[0].atStartOfDay();
        LocalDateTime to       = range[1].atStartOfDay();
        LocalDateTime prevFrom = prevRange[0].atStartOfDay();
        LocalDateTime prevTo   = prevRange[1].atStartOfDay();
        LocalDateTime now      = LocalDateTime.now(KST);

        List<Object[]> dailyRows = reservationRepository.findDailyMinutes(userId, from, to, now);
        List<Object[]> topRows   = reservationRepository.findTopSpaces(userId, from, to, now);
        List<Object[]> prevRows  = reservationRepository.findDailyMinutes(userId, prevFrom, prevTo, now);

        long totalMinutes = dailyRows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        long prevMinutes  = prevRows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        List<StatResponse.DailyItem> daily = dailyRows.stream()
                .map(r -> new StatResponse.DailyItem((String) r[0], ((Number) r[1]).longValue()))
                .toList();

        List<StatResponse.TopSpaceItem> topSpaces = topRows.stream()
                .map(r -> new StatResponse.TopSpaceItem(
                        (String) r[0], (String) r[1], ((Number) r[2]).longValue()))
                .toList();

        return new StatResponse(period, range[0].format(DATE_FMT), range[1].minusDays(1).format(DATE_FMT),
                totalMinutes, totalMinutes - prevMinutes, daily, topSpaces);
    }

    private LocalDate[] getRange(String period, LocalDate base) {
        return switch (period.toUpperCase()) {
            case "WEEKLY"  -> new LocalDate[]{base.with(java.time.DayOfWeek.MONDAY),
                                              base.with(java.time.DayOfWeek.MONDAY).plusWeeks(1)};
            case "MONTHLY" -> new LocalDate[]{base.withDayOfMonth(1),
                                              base.withDayOfMonth(1).plusMonths(1)};
            case "YEARLY"  -> new LocalDate[]{base.withDayOfYear(1),
                                              base.withDayOfYear(1).plusYears(1)};
            default        -> throw new BusinessException(ErrorCode.STAT_INVALID_PERIOD);
        };
    }

    private java.time.temporal.TemporalAmount getPeriodLength(String period) {
        return switch (period.toUpperCase()) {
            case "WEEKLY"  -> Period.ofWeeks(1);
            case "MONTHLY" -> Period.ofMonths(1);
            case "YEARLY"  -> Period.ofYears(1);
            default        -> Period.ofWeeks(1);
        };
    }
}
