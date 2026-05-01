package com.univsitdown.reservation.repository;

import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    // 시간 겹침 검증: A와 B가 겹치는 조건 → A.start < B.end AND A.end > B.start
    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.seat.id = :seatId
            AND r.status NOT IN ('CANCELED', 'NO_SHOW')
            AND r.startAt < :endAt AND r.endAt > :startAt
            """)
    boolean existsOverlapping(@Param("seatId") UUID seatId,
                              @Param("startAt") LocalDateTime startAt,
                              @Param("endAt") LocalDateTime endAt);

    // 연장 충돌 검증: 자신(excludeId)을 제외하고 새 종료 시각이 후속 예약과 겹치는지 확인
    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.seat.id = :seatId
            AND r.id <> :excludeId
            AND r.status NOT IN ('CANCELED', 'NO_SHOW')
            AND r.startAt < :endAt AND r.endAt > :startAt
            """)
    boolean existsOverlappingExclude(@Param("seatId") UUID seatId,
                                     @Param("excludeId") UUID excludeId,
                                     @Param("startAt") LocalDateTime startAt,
                                     @Param("endAt") LocalDateTime endAt);

    // 사용자 활성 예약 수 (SCHEDULED이고 아직 종료되지 않은 것)
    @Query("""
            SELECT COUNT(r) FROM Reservation r
            WHERE r.user.id = :userId
            AND r.status = 'SCHEDULED'
            AND r.endAt >= :now
            """)
    long countActiveByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    // SEAT-01: at 시점에 해당 공간에서 점유 중인 seat ID 목록
    @Query("""
            SELECT r.seat.id FROM Reservation r
            WHERE r.seat.space.id = :spaceId
            AND r.status NOT IN ('CANCELED', 'NO_SHOW')
            AND r.startAt <= :at AND r.endAt > :at
            """)
    List<UUID> findOccupiedSeatIdsBySpaceId(@Param("spaceId") UUID spaceId,
                                            @Param("at") LocalDateTime at);

    // RSV-02 ACTIVE: SCHEDULED이고 아직 종료 안 된 예약 (JOIN FETCH로 N+1 방지)
    @Query(value = """
            SELECT r FROM Reservation r JOIN FETCH r.seat s JOIN FETCH s.space
            WHERE r.user.id = :userId AND r.status = :status AND r.endAt >= :now
            ORDER BY r.startAt DESC
            """,
           countQuery = """
            SELECT COUNT(r) FROM Reservation r
            WHERE r.user.id = :userId AND r.status = :status AND r.endAt >= :now
            """)
    Page<Reservation> findActiveByUserId(@Param("userId") UUID userId,
                                         @Param("status") ReservationStatus status,
                                         @Param("now") LocalDateTime now,
                                         Pageable pageable);

    // RSV-02 PAST: SCHEDULED이고 이미 종료된 예약
    @Query(value = """
            SELECT r FROM Reservation r JOIN FETCH r.seat s JOIN FETCH s.space
            WHERE r.user.id = :userId AND r.status = :status AND r.endAt < :now
            ORDER BY r.startAt DESC
            """,
           countQuery = """
            SELECT COUNT(r) FROM Reservation r
            WHERE r.user.id = :userId AND r.status = :status AND r.endAt < :now
            """)
    Page<Reservation> findPastByUserId(@Param("userId") UUID userId,
                                       @Param("status") ReservationStatus status,
                                       @Param("now") LocalDateTime now,
                                       Pageable pageable);

    // Phase 5: 공간의 현재 점유 좌석 수 (CANCELED/NO_SHOW 제외, 현재 시각 기준)
    @Query("""
            SELECT COUNT(DISTINCT r.seat.id)
            FROM Reservation r
            WHERE r.seat.space.id = :spaceId
              AND r.status NOT IN ('CANCELED', 'NO_SHOW')
              AND r.startAt <= :now AND r.endAt > :now
            """)
    long countOccupiedBySpaceId(@Param("spaceId") UUID spaceId, @Param("now") LocalDateTime now);

    // RSV-02 CANCELED
    @Query(value = """
            SELECT r FROM Reservation r JOIN FETCH r.seat s JOIN FETCH s.space
            WHERE r.user.id = :userId AND r.status IN :statuses
            ORDER BY r.startAt DESC
            """,
           countQuery = """
            SELECT COUNT(r) FROM Reservation r
            WHERE r.user.id = :userId AND r.status IN :statuses
            """)
    Page<Reservation> findCanceledByUserId(@Param("userId") UUID userId,
                                           @Param("statuses") List<ReservationStatus> statuses,
                                           Pageable pageable);
}
