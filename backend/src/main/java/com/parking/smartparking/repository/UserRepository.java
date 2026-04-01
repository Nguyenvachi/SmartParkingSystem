package com.parking.smartparking.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.parking.smartparking.entity.User;

import jakarta.persistence.LockModeType;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

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

    default List<User> findAllManagedUsers() {
        return findAll(Sort.by(Sort.Order.asc("role"), Sort.Order.asc("fullName"), Sort.Order.asc("email")));
    }

    Page<User> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String email,
            String fullName,
            Pageable pageable);

    List<User> findByBranchCodeOrderByRoleAscFullNameAscEmailAsc(String branchCode);
}
