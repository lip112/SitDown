package com.univsitdown.space.domain;

import com.univsitdown.global.converter.StringListConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "spaces")
@Getter
// JPA 기본 생성자는 protected — 외부에서 new Space()로 만드는 것을 막아 create() 팩토리만 사용하도록 강제
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int floor;

    // DB에 "READING_ROOM" 같은 문자열로 저장. ordinal(숫자)은 enum 순서 변경 시 깨지므로 사용 금지.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SpaceCategory category;

    @Column(nullable = false)
    private LocalTime openTime;

    @Column(nullable = false)
    private LocalTime closeTime;

    // 공간별 단일 예약 최대 허용 시간. 비즈니스 기본값(BR-02)은 4시간.
    @Column(nullable = false)
    private int maxReservationHours;

    // PostgreSQL text[] 컬럼 — StringListConverter로 List<String> ↔ String[] 변환
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "text[]", nullable = false)
    private List<String> features;

    @Column(length = 500)
    private String thumbnailUrl;

    // 정적 팩토리 메서드: 외부에서 Space를 생성하는 유일한 진입점.
    // setter를 두지 않아 생성 이후 상태 변경을 메서드로만 허용 (현재는 변경 API 없음).
    public static Space create(String name, int floor, SpaceCategory category,
                               LocalTime openTime, LocalTime closeTime,
                               int maxReservationHours, List<String> features,
                               String thumbnailUrl) {
        Space space = new Space();
        space.name = name;
        space.floor = floor;
        space.category = category;
        space.openTime = openTime;
        space.closeTime = closeTime;
        space.maxReservationHours = maxReservationHours;
        // List.copyOf(): 호출자가 넘긴 리스트를 외부에서 수정해도 엔티티 내부 상태가 바뀌지 않도록 방어적 복사
        space.features = features != null ? List.copyOf(features) : List.of();
        space.thumbnailUrl = thumbnailUrl;
        return space;
    }
}
