package com.univsitdown.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String affiliation;

    @Column(length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static User create(String email, String passwordHash, String name,
                              String phone, String affiliation) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.name = name;
        user.phone = phone;
        user.affiliation = affiliation;
        user.role = UserRole.USER;
        user.createdAt = LocalDateTime.now();
        return user;
    }

    public void update(String name, String phone, String affiliation) {
        if (name != null) this.name = name;
        if (phone != null) this.phone = phone;
        if (affiliation != null) this.affiliation = affiliation;
    }
}
