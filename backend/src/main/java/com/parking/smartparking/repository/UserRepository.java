package com.parking.smartparking.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.parking.smartparking.entity.User;

/**
 * Repository cho Entity User Tầng giao tiếp với Database (Data Access Layer)
 *
 * JpaRepository cung cấp sẵn các method: - save(), findById(), findAll(),
 * deleteById()...
 *
 * Custom methods theo quy tắc Spring Data JPA: - findByEmail -> SELECT * FROM
 * users WHERE email = ? - existsByEmail -> SELECT COUNT(*) FROM users WHERE
 * email = ?
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Tìm user theo email (Dùng cho Login)
     *
     * @param email - Email người dùng
     * @return Optional<User> - Có thể null nếu không tìm thấy
     */
    Optional<User> findByEmail(String email);

    /**
     * Kiểm tra email đã tồn tại chưa (Dùng cho Register)
     *
     * @param email - Email cần kiểm tra
     * @return true nếu email đã tồn tại
     */
    boolean existsByEmail(String email);

    List<User> findByMembershipPlanAndAutoRenewMembershipTrueAndMembershipExpiryBefore(
            User.MembershipPlan membershipPlan,
            LocalDateTime time);
}
