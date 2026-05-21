package com.univsitdown.global.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeUtilsTest {

    @Test
    void Instant를_KST_문자열로_변환한다() {
        assertThat(DateTimeUtils.toKst(Instant.parse("2026-05-14T00:00:00Z")))
                .isEqualTo("2026-05-14 09:00:00");
    }

    @Test
    void LocalDateTime은_오프셋_없이_문자열로_변환한다() {
        assertThat(DateTimeUtils.toKst(LocalDateTime.of(2026, 5, 14, 9, 0)))
                .isEqualTo("2026-05-14 09:00:00");
    }
}
