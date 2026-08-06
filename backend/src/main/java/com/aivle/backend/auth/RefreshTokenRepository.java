package com.aivle.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;

public interface RefreshTokenRepository
    extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHashAndDeletedAtIsNull(String tokenHash);

    List<RefreshToken> findAllByUserIdAndDeletedAtIsNull(Long userId);
}
