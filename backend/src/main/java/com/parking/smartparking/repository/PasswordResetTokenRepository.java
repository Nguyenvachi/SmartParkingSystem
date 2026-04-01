package com.parking.smartparking.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.parking.smartparking.entity.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findTopByUser_IdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime now);
}
