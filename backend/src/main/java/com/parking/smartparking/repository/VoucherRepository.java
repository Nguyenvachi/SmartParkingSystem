package com.parking.smartparking.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.parking.smartparking.entity.Voucher;

import jakarta.persistence.LockModeType;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where lower(v.code) = lower(:code)")
    Optional<Voucher> findByCodeForUpdate(@Param("code") String code);

    List<Voucher> findByActiveTrueAndRemainingUsesGreaterThanAndValidUntilAfterOrderByValidUntilAsc(
            Integer remainingUses,
            LocalDateTime validUntil);
}
