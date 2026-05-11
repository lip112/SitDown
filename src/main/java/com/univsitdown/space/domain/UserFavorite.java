package com.univsitdown.space.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_favorites",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "space_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID spaceId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static UserFavorite of(UUID userId, UUID spaceId) {
        UserFavorite fav = new UserFavorite();
        fav.userId = userId;
        fav.spaceId = spaceId;
        fav.createdAt = Instant.now();
        return fav;
    }
}
