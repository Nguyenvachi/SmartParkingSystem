package com.parking.smartparking.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.dto.request.AdminUserUpdateRequest;
import com.parking.smartparking.dto.response.AdminDashboardSummaryResponse;
import com.parking.smartparking.dto.response.AdminUserResponse;
import com.parking.smartparking.entity.BlacklistedVehicle;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.BlacklistedVehicleRepository;
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

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary(String requesterEmail) {
        User requester = findRequester(requesterEmail);
        List<ParkingSlot> visibleSlots = requester.isBranchAdmin()
                ? parkingSlotRepository.findAllVisibleByBranchCode(normalizeBranchCode(requester.getBranchCode()))
                : parkingSlotRepository.findAll();

        List<User> visibleUsers = requester.isBranchAdmin()
                ? userRepository.findByBranchCodeOrderByRoleAscFullNameAscEmailAsc(normalizeBranchCode(requester.getBranchCode()))
                : userRepository.findAllManagedUsers();

        long activeBlacklistEntries = (requester.isBranchAdmin()
                ? blacklistedVehicleRepository.findVisibleByBranchCode(normalizeBranchCode(requester.getBranchCode()))
                : blacklistedVehicleRepository.findAll()).stream()
                .filter(BlacklistedVehicle::getActive)
                .count();

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
                .walletBalance(user.getWalletBalance())
                .membershipExpiry(user.getMembershipExpiry())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

