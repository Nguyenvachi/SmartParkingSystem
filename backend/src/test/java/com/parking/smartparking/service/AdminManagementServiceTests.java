package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.parking.smartparking.dto.request.AdminUserUpdateRequest;
import com.parking.smartparking.dto.response.AdminDashboardSummaryResponse;
import com.parking.smartparking.dto.response.AdminUserResponse;
import com.parking.smartparking.entity.BlacklistedVehicle;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.BlacklistedVehicleRepository;
import com.parking.smartparking.repository.ParkingSlotRepository;
import com.parking.smartparking.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AdminManagementServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ParkingSlotRepository parkingSlotRepository;

    @Mock
    private BlacklistedVehicleRepository blacklistedVehicleRepository;

    @Test
    void shouldReturnBranchScopedSummaryForBranchAdmin() {
        AdminManagementService service = createService();
        User branchAdmin = User.builder()
                .id(1L)
                .email("branch-admin@test.com")
                .role(User.Role.ROLE_BRANCH_ADMIN)
                .branchCode("HCM")
                .build();

        ParkingSlot availableSlot = ParkingSlot.builder().slotName("A01").status("AVAILABLE").branchCode("HCM").build();
        ParkingSlot occupiedSlot = ParkingSlot.builder().slotName("A02").status("OCCUPIED").branchCode("HCM").build();
        BlacklistedVehicle activeEntry = BlacklistedVehicle.builder().plateNumber("51A12345").active(true).branchCode("HCM").build();
        BlacklistedVehicle inactiveEntry = BlacklistedVehicle.builder().plateNumber("51A99999").active(false).branchCode("HCM").build();

        when(userRepository.findByEmail("branch-admin@test.com")).thenReturn(Optional.of(branchAdmin));
        when(parkingSlotRepository.findAllVisibleByBranchCode("HCM")).thenReturn(List.of(availableSlot, occupiedSlot));
        when(userRepository.findByBranchCodeOrderByRoleAscFullNameAscEmailAsc("HCM")).thenReturn(List.of(branchAdmin));
        when(blacklistedVehicleRepository.findVisibleByBranchCode("HCM")).thenReturn(List.of(activeEntry, inactiveEntry));

        AdminDashboardSummaryResponse summary = service.getSummary("branch-admin@test.com");

        assertFalse(summary.isGlobalAdmin());
        assertEquals(1, summary.getTotalVisibleUsers());
        assertEquals(2, summary.getTotalVisibleSlots());
        assertEquals(1, summary.getAvailableSlots());
        assertEquals(1, summary.getOccupiedSlots());
        assertEquals(1, summary.getActiveBlacklistEntries());
    }

    @Test
    void shouldAllowGlobalAdminToUpdateUserAccess() {
        AdminManagementService service = createService();
        User globalAdmin = User.builder()
                .id(1L)
                .email("admin@test.com")
                .role(User.Role.ROLE_ADMIN)
                .build();
        User targetUser = User.builder()
                .id(2L)
                .email("user@test.com")
                .fullName("Target User")
                .role(User.Role.ROLE_USER)
                .walletBalance(new BigDecimal("10000"))
                .build();

        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setRole("ROLE_BRANCH_ADMIN");
        request.setBranchCode("dn");

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(globalAdmin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserResponse response = service.updateUserAccess(2L, request, "admin@test.com");

        assertEquals("ROLE_BRANCH_ADMIN", response.getRole());
        assertEquals("DN", response.getBranchCode());
        verify(userRepository).save(targetUser);
    }

    @Test
    void shouldRejectBranchAdminWhenManagingUsers() {
        AdminManagementService service = createService();
        User branchAdmin = User.builder()
                .id(3L)
                .email("branch@test.com")
                .role(User.Role.ROLE_BRANCH_ADMIN)
                .branchCode("HCM")
                .build();

        when(userRepository.findByEmail("branch@test.com")).thenReturn(Optional.of(branchAdmin));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.getManagedUsers("branch@test.com"));

        assertEquals("Chỉ admin tổng mới được quản lý người dùng và phân quyền.", exception.getMessage());
    }

    private AdminManagementService createService() {
        return new AdminManagementService(userRepository, parkingSlotRepository, blacklistedVehicleRepository);
    }
}
