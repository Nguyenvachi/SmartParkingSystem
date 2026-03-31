package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.dto.request.AdminUserUpdateRequest;
import com.parking.smartparking.dto.response.AdminDashboardSummaryResponse;
import com.parking.smartparking.dto.response.AdminUserResponse;
import com.parking.smartparking.dto.response.PagedResponse;
import com.parking.smartparking.entity.BlacklistedVehicle;
import com.parking.smartparking.entity.Booking;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.BlacklistedVehicleRepository;
import com.parking.smartparking.repository.BookingRepository;
import com.parking.smartparking.repository.ParkingSlotRepository;
import com.parking.smartparking.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AdminManagementService {

    private static final String DEFAULT_BRANCH = "MAIN";

    private final UserRepository userRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final BlacklistedVehicleRepository blacklistedVehicleRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary(String requesterEmail) {
        User requester = findRequester(requesterEmail);

        boolean branchRestricted = requester.isBranchAdmin();
        String branchCode = normalizeBranchCode(requester.getBranchCode());

        List<ParkingSlot> visibleSlots = requester.isBranchAdmin()
                ? parkingSlotRepository.findAllVisibleByBranchCode(branchCode)
                : parkingSlotRepository.findAll();

        List<User> visibleUsers = requester.isBranchAdmin()
                ? userRepository.findByBranchCodeOrderByRoleAscFullNameAscEmailAsc(branchCode)
                : userRepository.findAllManagedUsers();

        long activeBlacklistEntries = (requester.isBranchAdmin()
                ? blacklistedVehicleRepository.findVisibleByBranchCode(branchCode)
                : blacklistedVehicleRepository.findAll()).stream()
                .filter(BlacklistedVehicle::getActive)
                .count();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        BigDecimal revenueToday = branchRestricted
                ? bookingRepository.sumTotalAmountByStatusAndCheckOutTimeBetweenForBranch(Booking.BookingStatus.COMPLETED, branchCode, startOfToday, now)
                : bookingRepository.sumTotalAmountByStatusAndCheckOutTimeBetween(Booking.BookingStatus.COMPLETED, startOfToday, now);

        BigDecimal revenueThisMonth = branchRestricted
                ? bookingRepository.sumTotalAmountByStatusAndCheckOutTimeBetweenForBranch(Booking.BookingStatus.COMPLETED, branchCode, startOfMonth, now)
                : bookingRepository.sumTotalAmountByStatusAndCheckOutTimeBetween(Booking.BookingStatus.COMPLETED, startOfMonth, now);

        BigDecimal revenueAllTime = branchRestricted
                ? bookingRepository.sumTotalAmountByStatusForBranch(Booking.BookingStatus.COMPLETED, branchCode)
                : bookingRepository.sumTotalAmountByStatus(Booking.BookingStatus.COMPLETED);

        long completedToday = branchRestricted
                ? bookingRepository.countByStatusAndCheckOutTimeBetweenForBranch(Booking.BookingStatus.COMPLETED, branchCode, startOfToday, now)
                : bookingRepository.countByStatusAndCheckOutTimeBetween(Booking.BookingStatus.COMPLETED, startOfToday, now);

        long completedThisMonth = branchRestricted
                ? bookingRepository.countByStatusAndCheckOutTimeBetweenForBranch(Booking.BookingStatus.COMPLETED, branchCode, startOfMonth, now)
                : bookingRepository.countByStatusAndCheckOutTimeBetween(Booking.BookingStatus.COMPLETED, startOfMonth, now);

        return AdminDashboardSummaryResponse.builder()
                .role(requester.getRole().name())
                .branchCode(requester.getBranchCode())
                .globalAdmin(requester.isGlobalAdmin())
                .totalVisibleUsers(visibleUsers.size())
                .totalVisibleSlots(visibleSlots.size())
                .availableSlots(countByStatus(visibleSlots, "AVAILABLE"))
                .reservedSlots(countByStatus(visibleSlots, "RESERVED"))
                .occupiedSlots(countByStatus(visibleSlots, "OCCUPIED"))
                .maintenanceSlots(countByStatus(visibleSlots, "MAINTENANCE"))
                .activeBlacklistEntries(activeBlacklistEntries)
                .revenueToday(revenueToday)
                .revenueThisMonth(revenueThisMonth)
                .revenueAllTime(revenueAllTime)
                .completedBookingsToday(completedToday)
                .completedBookingsThisMonth(completedThisMonth)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getManagedUsers(String requesterEmail) {
        User requester = findRequester(requesterEmail);
        ensureGlobalAdmin(requester);

        return userRepository.findAllManagedUsers().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<AdminUserResponse> searchManagedUsers(
            String requesterEmail,
            String keyword,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        User requester = findRequester(requesterEmail);
        ensureGlobalAdmin(requester);

        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        String safeSortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));

        Page<User> result;
        String q = keyword == null ? "" : keyword.trim();
        if (q.isBlank()) {
            result = userRepository.findAll(pageable);
        } else {
            result = userRepository.findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(q, q, pageable);
        }

        return PagedResponse.<AdminUserResponse>builder()
                .items(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public AdminUserResponse updateUserAccess(Long userId, AdminUserUpdateRequest request, String requesterEmail) {
        User requester = findRequester(requesterEmail);
        ensureGlobalAdmin(requester);

        if (requester.getId().equals(userId)) {
            throw new RuntimeException("Admin tổng không được tự thay đổi quyền của chính mình.");
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user #" + userId));

        User.Role newRole = User.Role.valueOf(request.getRole());
        targetUser.setRole(newRole);

        switch (newRole) {
            case ROLE_BRANCH_ADMIN -> {
                if (request.getBranchCode() == null || request.getBranchCode().isBlank()) {
                    throw new RuntimeException("ROLE_BRANCH_ADMIN bắt buộc phải có mã chi nhánh.");
                }
                targetUser.setBranchCode(normalizeBranchCode(request.getBranchCode()));
            }
            case ROLE_ADMIN ->
                targetUser.setBranchCode(null);
            case ROLE_USER ->
                targetUser.setBranchCode(request.getBranchCode() == null || request.getBranchCode().isBlank()
                        ? null
                        : normalizeBranchCode(request.getBranchCode()));
        }

        return toResponse(userRepository.save(targetUser));
    }

    @Transactional
    public AdminUserResponse updateUserStatus(Long userId, boolean active, String requesterEmail) {
        User requester = findRequester(requesterEmail);
        ensureGlobalAdmin(requester);

        if (requester.getId().equals(userId)) {
            throw new RuntimeException("Admin tổng không được tự khóa/mở tài khoản của chính mình.");
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user #" + userId));

        if (!active && targetUser.isGlobalAdmin()) {
            throw new RuntimeException("Không được vô hiệu hóa tài khoản Admin tổng.");
        }

        targetUser.setIsActive(active);
        return toResponse(userRepository.save(targetUser));
    }

    private long countByStatus(List<ParkingSlot> slots, String status) {
        return slots.stream().filter(slot -> status.equalsIgnoreCase(slot.getStatus())).count();
    }

    private User findRequester(String requesterEmail) {
        return userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user đăng nhập: " + requesterEmail));
    }

    private void ensureGlobalAdmin(User requester) {
        if (!requester.isGlobalAdmin()) {
            throw new RuntimeException("Chỉ admin tổng mới được quản lý người dùng và phân quyền.");
        }
    }

    private String normalizeBranchCode(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) {
            return DEFAULT_BRANCH;
        }
        return branchCode.trim().toUpperCase(Locale.ROOT);
    }

    private AdminUserResponse toResponse(User user) {
        return AdminUserResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .branchCode(user.getBranchCode())
                .emailVerified(user.getIsEmailVerified())
                .active(user.getIsActive())
                .walletBalance(user.getWalletBalance())
                .membershipExpiry(user.getMembershipExpiry())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
