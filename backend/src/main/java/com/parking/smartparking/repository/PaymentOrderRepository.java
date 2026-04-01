package com.parking.smartparking.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.parking.smartparking.entity.PaymentOrder;
import com.parking.smartparking.entity.PaymentOrder.Provider;
import com.parking.smartparking.entity.PaymentOrder.Purpose;
import com.parking.smartparking.entity.PaymentOrder.Status;

import jakarta.persistence.LockModeType;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentOrder p where p.orderId = :orderId")
    Optional<PaymentOrder> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Query("""
            select p from PaymentOrder p
            where p.user.id = :userId
              and p.provider = :provider
              and p.purpose = :purpose
              and p.status = :status
              and p.createdAt >= :since
            order by p.createdAt desc
            """)
    Optional<PaymentOrder> findRecentByUserAndProviderAndPurposeAndStatus(
            @Param("userId") Long userId,
            @Param("provider") Provider provider,
            @Param("purpose") Purpose purpose,
            @Param("status") Status status,
            @Param("since") LocalDateTime since);
}
