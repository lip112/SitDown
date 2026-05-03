package com.univsitdown.space.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    @Column(nullable = false)
    private int rowNum;

    @Column(nullable = false)
    private int colNum;

    @Column(nullable = false, length = 20)
    private String label;

    @Column(nullable = false)
    private boolean isEnabled;

    @Getter(AccessLevel.NONE)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] features;

    public List<String> getFeatures() {
        return (features == null || features.length == 0) ? List.of() : Arrays.asList(features);
    }

    public static Seat create(Space space, int rowNum, int colNum, String label) {
        Seat seat = new Seat();
        seat.space = space;
        seat.rowNum = rowNum;
        seat.colNum = colNum;
        seat.label = label;
        seat.isEnabled = true;
        seat.features = new String[0];
        return seat;
    }

    public void updateEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }
}
