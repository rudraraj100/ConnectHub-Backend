package com.auth_service.repository;

import com.auth_service.entity.User;
import com.auth_service.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * UserRepository — §4.1 case study.
 * findByEmail, findByUsername, findByUserId, existsByEmail,
 * existsByUsername, findByStatus, searchByUsername, deleteByUserId.
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findByStatus(UserStatus status);

    List<User> findByIsActiveTrue();

    /** Case-insensitive username search for user discovery — §2.2 */
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) AND u.isActive = true")
    List<User> searchByUsername(@Param("keyword") String keyword);

    /**
     * Direct SQL update for suspend/reinstate — bypasses Lombok getter/setter
     * ambiguity and JPA dirty-checking entirely. Guaranteed to persist.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE users SET is_active = :active WHERE user_id = :userId", nativeQuery = true)
    void setIsActive(@Param("userId") String userId, @Param("active") boolean active);

    void deleteByUserId(String userId);

    Optional<User> findByProviderAndProviderId(
            com.auth_service.entity.AuthProvider provider, String providerId);
}
