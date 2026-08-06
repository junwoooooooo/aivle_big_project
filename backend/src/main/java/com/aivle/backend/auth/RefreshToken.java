package com.aivle.backend.auth;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, unique = true, length = 100)
    private String tokenJti;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;
    private LocalDateTime lastUsedAt;

    private RefreshToken(
        User user,
        String tokenHash,
        String tokenJti,
        LocalDateTime expiresAt
    ) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.tokenJti = tokenJti;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(
        User user,
        String tokenHash,
        String tokenJti,
        LocalDateTime expiresAt
    ) {
        return new RefreshToken(user, tokenHash, tokenJti, expiresAt);
    }

    public boolean isUsableAt(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now) && !isDeleted();
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
        this.lastUsedAt = now;
    }
}
