package com.aivle.backend.user.repository;
import com.aivle.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByUsername(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByRoleAndStatusAndDeletedAtIsNull(com.aivle.backend.common.entity.UserRole role, com.aivle.backend.common.entity.UserStatus status);
    long countByRoleAndDeletedAtIsNull(com.aivle.backend.common.entity.UserRole role);
    long countByStatusAndDeletedAtIsNull(com.aivle.backend.common.entity.UserStatus status);
    long countByDeletedAtIsNull();

    @Query("""
        select u from User u where u.deletedAt is null
        and (:keyword is null or lower(u.username) like lower(concat('%', :keyword, '%'))
             or lower(coalesce(u.email, '')) like lower(concat('%', :keyword, '%'))
             or lower(u.name) like lower(concat('%', :keyword, '%')))
        and (:role is null or u.role = :role)
        and (:status is null or u.status = :status)
        """)
    Page<User> searchAdminUsers(@Param("keyword") String keyword,
                                @Param("role") com.aivle.backend.common.entity.UserRole role,
                                @Param("status") com.aivle.backend.common.entity.UserStatus status,
                                Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.status = :status and u.deletedAt is null")
    List<User> findByRoleAndStatusForUpdate(@Param("role") com.aivle.backend.common.entity.UserRole role,
                                             @Param("status") com.aivle.backend.common.entity.UserStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForDeletionUpdate(@Param("userId") Long userId);
}
