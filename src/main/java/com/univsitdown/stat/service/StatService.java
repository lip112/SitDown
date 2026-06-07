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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StatService {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public StatResponse getStat(UUID userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessException(ErrorCode.STAT_INVALID_PERIOD);
        }

        LocalDate toExclusive = to.plusDays(1);
        long days = ChronoUnit.DAYS.between(from, toExclusive);
        LocalDate prevFrom = from.minusDays(days);

        return getStat(userId, from, toExclusive, prevFrom, from);
    }

    private StatResponse getStat(UUID userId, LocalDate fromDate, LocalDate toDate,
                                 LocalDate prevFromDate, LocalDate prevToDate) {
        LocalDateTime from     = fromDate.atStartOfDay();
        LocalDateTime to       = toDate.atStartOfDay();
        LocalDateTime prevFrom = prevFromDate.atStartOfDay();
        LocalDateTime prevTo   = prevToDate.atStartOfDay();
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

        return new StatResponse(fromDate.format(DATE_FMT), toDate.minusDays(1).format(DATE_FMT),
                totalMinutes, totalMinutes - prevMinutes, daily, topSpaces);
    }
}
