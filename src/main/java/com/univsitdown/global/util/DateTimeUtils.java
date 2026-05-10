package com.univsitdown.global.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtils {

    private static final DateTimeFormatter KST_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'+09:00'");

    private DateTimeUtils() {}

    public static String nowKst() {
        return Instant.now().atOffset(ZoneOffset.ofHours(9)).format(KST_FORMATTER);
    }

    public static String nowPlusSecondsKst(long seconds) {
        return Instant.now().plusSeconds(seconds).atOffset(ZoneOffset.ofHours(9)).format(KST_FORMATTER);
    }

    public static String toKst(LocalDateTime ldt) {
        return ldt.format(KST_FORMATTER);
    }

    public static String toKst(Instant instant) {
        return instant.atOffset(ZoneOffset.ofHours(9)).format(KST_FORMATTER);
    }
}
