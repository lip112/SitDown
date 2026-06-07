package com.univsitdown.stat.repository;

import com.univsitdown.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationStatRepositoryTest {

    @Test
    void statQueries_미래예약은_이용시간집계에서_제외한다() throws NoSuchMethodException {
        assertThat(queryValue("findDailyMinutes"))
                .contains("AND start_at <= :now");
        assertThat(queryValue("findTopSpaces"))
                .contains("AND r.start_at <= :now");
    }

    private String queryValue(String methodName) throws NoSuchMethodException {
        Method method = ReservationRepository.class.getMethod(
                methodName, UUID.class, LocalDateTime.class, LocalDateTime.class, LocalDateTime.class);
        return method.getAnnotation(Query.class).value();
    }
}
