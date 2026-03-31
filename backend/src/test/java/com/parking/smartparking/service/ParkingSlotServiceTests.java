package com.parking.smartparking.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.parking.smartparking.controller.WebSocketController;
import com.parking.smartparking.dto.request.ParkingSlotRequest;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.BookingRepository;
import com.parking.smartparking.repository.ParkingSlotRepository;
import com.parking.smartparking.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ParkingSlotServiceTests {

    @Mock
    private ParkingSlotRepository parkingSlotRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private WebSocketController webSocketController;

    @Mock
    private UserRepository userRepository;

    @Test
    void shouldRecommendClosestAvailableSlotForRequestedType() {
        ParkingSlotService parkingSlotService = createService();
        ParkingSlot a01 = ParkingSlot.builder()
                .id(1L)
                .slotName("A01")
                .type("SEDAN")
                .status("AVAILABLE")
                .pricePerHour(new BigDecimal("5000"))
                .build();
        ParkingSlot c04 = ParkingSlot.builder()
                .id(2L)
                .slotName("C04")
                .type("SEDAN")
                .status("AVAILABLE")
                .pricePerHour(new BigDecimal("4500"))
                .build();
        ParkingSlot b02 = ParkingSlot.builder()
                .id(3L)
                .slotName("B02")
                .type("SUV")
                .status("AVAILABLE")
                .pricePerHour(new BigDecimal("6000"))
                .build();

        when(parkingSlotRepository.findByStatusOrderBySlotNameAsc("AVAILABLE"))
                .thenReturn(List.of(c04, b02, a01));

        var response = parkingSlotService.recommendSlot("SEDAN", null);

        assertEquals("SEDAN", response.getRequestedType());
        assertEquals("A01", response.getRecommendedSlot().getSlotName());
        assertEquals(List.of("C04"), response.getAlternativeSlots().stream().map(slot -> slot.getSlotName()).toList());
    }

    @Test
    void shouldFallbackToAnyAvailableSlotWhenRequestedTypeIsMissing() {
        ParkingSlotService parkingSlotService = createService();
        ParkingSlot a04 = ParkingSlot.builder()
                .id(4L)
                .slotName("A04")
                .type("SUV")
                .status("AVAILABLE")
                .pricePerHour(new BigDecimal("6500"))
                .build();
        ParkingSlot d03 = ParkingSlot.builder()
                .id(5L)
                .slotName("D03")
                .type("SUV")
                .status("AVAILABLE")
                .pricePerHour(new BigDecimal("5500"))
                .build();

        when(parkingSlotRepository.findByStatusOrderBySlotNameAsc("AVAILABLE"))
                .thenReturn(List.of(d03, a04));

        var response = parkingSlotService.recommendSlot("SEDAN", null);

        assertEquals("A04", response.getRecommendedSlot().getSlotName());
        assertEquals("Không còn slot đúng loại xe, hệ thống fallback sang slot trống gần cổng ra và thang máy nhất.",
                response.getExplanation());
    }

    @Test
    void shouldBroadcastRealtimeUpdateWhenCreatingSlot() {
        ParkingSlotService parkingSlotService = createService();
        ParkingSlotRequest request = new ParkingSlotRequest();
        request.setSlotName("A01");
        request.setType("SEDAN");
        request.setStatus("AVAILABLE");
        request.setPricePerHour(new BigDecimal("5000"));

        ParkingSlot saved = ParkingSlot.builder()
                .id(10L)
                .slotName("A01")
                .type("SEDAN")
                .status("AVAILABLE")
                .pricePerHour(new BigDecimal("5000"))
                .version(0L)
                .build();

        when(parkingSlotRepository.findBySlotName("A01")).thenReturn(Optional.empty());
        when(parkingSlotRepository.save(any(ParkingSlot.class))).thenReturn(saved);

        parkingSlotService.createSlot(request, null);

        verify(webSocketController).sendSlotUpdate(any());
    }

    @Test
    void shouldBroadcastDeletedStatusWhenRemovingSlot() {
        ParkingSlotService parkingSlotService = createService();
        ParkingSlot slot = ParkingSlot.builder()
                .id(11L)
                .slotName("B02")
                .type("SUV")
                .status("AVAILABLE")
                .pricePerHour(new BigDecimal("7000"))
                .version(3L)
                .build();

        when(parkingSlotRepository.findById(11L)).thenReturn(Optional.of(slot));

        parkingSlotService.deleteSlot(11L, null);

        verify(webSocketController).sendSlotUpdate(any());
    }

    @Test
    void shouldRejectUnsupportedVehicleTypeRecommendation() {
        ParkingSlotService parkingSlotService = createService();

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> parkingSlotService.recommendSlot("TRUCK", null));

        assertEquals("Loại xe gợi ý chỉ hỗ trợ SEDAN hoặc SUV.", exception.getMessage());
    }

    @Test
    void shouldLimitBranchAdminToOwnBranchSlots() {
        ParkingSlotService parkingSlotService = createService();
        User branchAdmin = User.builder()
                .email("admin.hcm@test.com")
                .role(User.Role.ROLE_BRANCH_ADMIN)
                .branchCode("HCM")
                .build();
        ParkingSlot hcmSlot = ParkingSlot.builder()
                .id(20L)
                .slotName("A01")
                .type("SEDAN")
                .status("AVAILABLE")
                .branchCode("HCM")
                .pricePerHour(new BigDecimal("5000"))
                .build();

        when(userRepository.findByEmail("admin.hcm@test.com")).thenReturn(Optional.of(branchAdmin));
        when(parkingSlotRepository.findAllVisibleByBranchCode("HCM")).thenReturn(List.of(hcmSlot));

        var slots = parkingSlotService.getAllSlots("admin.hcm@test.com");

        assertEquals(1, slots.size());
        assertEquals("HCM", slots.get(0).getBranchCode());
    }

    @Test
    void shouldAutoAssignBranchForBranchAdminWhenCreatingSlot() {
        ParkingSlotService parkingSlotService = createService();
        User branchAdmin = User.builder()
                .email("admin.dn@test.com")
                .role(User.Role.ROLE_BRANCH_ADMIN)
                .branchCode("DN")
                .build();
        ParkingSlotRequest request = new ParkingSlotRequest();
        request.setSlotName("C03");
        request.setType("SUV");
        request.setStatus("AVAILABLE");
        request.setPricePerHour(new BigDecimal("7000"));

        ParkingSlot saved = ParkingSlot.builder()
                .id(30L)
                .slotName("C03")
                .type("SUV")
                .status("AVAILABLE")
                .branchCode("DN")
                .pricePerHour(new BigDecimal("7000"))
                .version(0L)
                .build();

        when(userRepository.findByEmail("admin.dn@test.com")).thenReturn(Optional.of(branchAdmin));
        when(parkingSlotRepository.findBySlotName("C03")).thenReturn(Optional.empty());
        when(parkingSlotRepository.save(any(ParkingSlot.class))).thenReturn(saved);

        var response = parkingSlotService.createSlot(request, "admin.dn@test.com");

        assertEquals("DN", response.getBranchCode());
    }

    private ParkingSlotService createService() {
        return new ParkingSlotService(parkingSlotRepository, bookingRepository, userRepository, webSocketController);
    }
}
