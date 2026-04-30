package com.univsitdown.reservation.service;

import com.univsitdown.reservation.dto.CreateReservationRequest;
import com.univsitdown.reservation.exception.SeatAlreadyReservedException;
import com.univsitdown.reservation.exception.UserReservationLimitException;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import com.univsitdown.user.domain.User;
import com.univsitdown.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "jwt.secret=test-secret-key-for-concurrency-test-minimum-32-chars",
        "spring.data.redis.host=invalid-host-fallback-to-memory"
})
@Testcontainers
class ReservationConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("sitdown_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ReservationService reservationService;
    @Autowired UserRepository userRepository;
    @Autowired SpaceRepository spaceRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private List<UUID> userIds;
    private UUID seatId;

    @BeforeEach
    void setUp() {
        Space space = spaceRepository.save(Space.create(
                "동시성테스트열람실_" + UUID.randomUUID(), 1, SpaceCategory.READING_ROOM,
                LocalTime.of(0, 0), LocalTime.of(23, 59), 4, List.of(), null));

        com.univsitdown.space.domain.Seat seat =
                seatRepository.save(com.univsitdown.space.domain.Seat.create(space, 1, 1, "A-1"));
        seatId = seat.getId();

        userIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User user = userRepository.save(User.create(
                    "concurrent_" + UUID.randomUUID() + "@test.com",
                    passwordEncoder.encode("Conc0rr3nt!"),
                    "테스터" + i, null, null));
            userIds.add(user.getId());
        }
    }

    @Test
    void 동시에_10명이_같은_좌석을_예약하면_1명만_성공한다() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        // 미래 시간으로 설정 (운영 시간 내)
        LocalDateTime startAt = LocalDateTime.of(2027, 6, 1, 9, 0);
        LocalDateTime endAt = LocalDateTime.of(2027, 6, 1, 11, 0);

        for (int i = 0; i < threadCount; i++) {
            final UUID uid = userIds.get(i);
            executor.submit(() -> {
                try {
                    reservationService.reserve(uid, new CreateReservationRequest(seatId, startAt, endAt));
                    success.incrementAndGet();
                } catch (SeatAlreadyReservedException | UserReservationLimitException e) {
                    conflict.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(9);
    }
}
