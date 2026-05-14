package com.univsitdown.reservation.domain;

import com.univsitdown.space.domain.Seat;
import com.univsitdown.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    private LocalDateTime canceledAt;

    @Column(nullable = false)
    private int extendedCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Reservation create(User user, Seat seat, LocalDateTime startAt, LocalDateTime endAt) {
        Reservation r = new Reservation();
        r.user = user;
        r.seat = seat;
        r.startAt = startAt;
        r.endAt = endAt;
        r.status = ReservationStatus.SCHEDULED;
        r.extendedCount = 0;
        r.createdAt = LocalDateTime.now();
        return r;
    }

    /**
     * DB에는 SCHEDULED / CANCELED / NO_SHOW 만 영속화한다.
     * IN_USE / COMPLETED 는 상태 전이 스케줄러 없이 now 기준으로 계산해 반환한다.
     * 이로써 상태 동기화 배치 없이도 항상 정확한 상태를 제공할 수 있다.
     */
    public ReservationStatus computedStatus(LocalDateTime now) {
        if (status == ReservationStatus.CANCELED || status == ReservationStatus.NO_SHOW) {
            return status;
        }
        if (endAt.isBefore(now)) return ReservationStatus.COMPLETED;
        if (!startAt.isAfter(now)) return ReservationStatus.IN_USE;
        return ReservationStatus.SCHEDULED;
    }

    public void extend(LocalDateTime newEndAt) {
        this.endAt = newEndAt;
        this.extendedCount++;
    }

    public void cancel(LocalDateTime now) {
        this.status = ReservationStatus.CANCELED;
        this.canceledAt = now;
    }
}
