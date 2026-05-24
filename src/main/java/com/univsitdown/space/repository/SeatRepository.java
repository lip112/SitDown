package com.univsitdown.space.repository;

import com.univsitdown.space.domain.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findBySpaceIdOrderByRowNumAscColNumAsc(UUID spaceId);

    boolean existsBySpaceId(UUID spaceId);

    void deleteBySpaceId(UUID spaceId);

    long countBySpaceIdAndIsEnabledTrue(UUID spaceId);

    @Query("SELECT COALESCE(MAX(s.rowNum), 0) FROM Seat s WHERE s.space.id = :spaceId")
    int findMaxRowBySpaceId(@Param("spaceId") UUID spaceId);

    @Query("SELECT COALESCE(MAX(s.colNum), 0) FROM Seat s WHERE s.space.id = :spaceId")
    int findMaxColBySpaceId(@Param("spaceId") UUID spaceId);

    // 예약 생성 시 row-level 비관적 락 획득 — 동시 요청이 순차 처리되도록 강제
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") UUID id);
}
