package com.aivle.backend.admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
public interface AdminActionTokenRepository extends JpaRepository<AdminActionToken,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 Optional<AdminActionToken> findByTokenHash(String tokenHash);
}
