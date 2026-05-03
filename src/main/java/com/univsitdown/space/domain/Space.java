package com.univsitdown.space.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "spaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int floor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SpaceCategory category;

    @Column(nullable = false)
    private LocalTime openTime;

    @Column(nullable = false)
    private LocalTime closeTime;

    @Column(nullable = false)
    private int maxReservationHours;

    // Hibernate 6 + PostgreSQL text[]: AttributeConverter<List, String[]> 조합은 ClassCastException 발생.
    // String[] 필드로 저장하고 getFeatures()로 List<String> 뷰를 제공한다.
    @Getter(AccessLevel.NONE)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] features;

    @Column(length = 500)
    private String thumbnailUrl;

    public List<String> getFeatures() {
        return (features == null || features.length == 0) ? List.of() : Arrays.asList(features);
    }

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
        space.features = features != null ? features.toArray(new String[0]) : new String[0];
        space.thumbnailUrl = thumbnailUrl;
        return space;
    }
}
