package com.soyunju.logcollector.domain.admin;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String role; // ADMIN

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AdminUser(String username, String passwordHash, String role) {
        this.username    = username;
        this.passwordHash = passwordHash;
        this.role        = role;
        this.createdAt   = LocalDateTime.now();
    }
}