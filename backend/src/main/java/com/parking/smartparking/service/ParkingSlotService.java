package com.parking.smartparking.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.controller.WebSocketController;
import com.parking.smartparking.dto.request.ParkingSlotRequest;
import com.parking.smartparking.dto.response.ParkingRecommendationResponse;
import com.parking.smartparking.dto.response.ParkingSlotResponse;
import com.parking.smartparking.entity.Booking;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.entity.User;
import com.parking.smartparking.repository.BookingRepository;
import com.parking.smartparking.repository.ParkingSlotRepository;
import com.parking.smartparking.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service xử lý logic CRUD cho Parking Slot
 *
 * Tech Key: - Tính năng #1: Optimistic Locking (JPA tự xử lý qua @Version) -
 * Tính năng #9: Data Isolation (Filter theo quyền trong Controller)
 *
 * Quy tắc: - Method tìm kiếm: READ-ONLY (không cần @Transactional) - Method
 * thay đổi dữ liệu: Phải có @Transactional
 */
@Service
@RequiredArgsConstructor
public class ParkingSlotService {

    private static final int EXIT_WEIGHT = 5;
    private static final int ELEVATOR_WEIGHT = 3;
    private static final String DEFAULT_BRANCH = "MAIN";
    private final ParkingSlotRepository parkingSlotRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    // [FIX 3 - Tech #5 WebSocket] Inject để gửi real-time update khi slot thay đổi
    private final WebSocketController webSocketController;

    /**
     * Lấy tất cả parking slots
     *
     * @return List<ParkingSlotResponse>
     */
    public List<ParkingSlotResponse> getAllSlots(String requesterEmail) {
        BranchAccess branchAccess = resolveBranchAccess(requesterEmail);
        List<ParkingSlot> slots = branchAccess.restricted()
                ? parkingSlotRepository.findAllVisibleByBranchCode(branchAccess.branchCode())
                : parkingSlotRepository.findAll();

        Map<Long, Booking> activeBookings = resolveActiveBookingsBySlotId(slots);

        return slots.stream()
                .map(slot -> convertToResponse(slot, activeBookings.get(slot.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách slot theo trạng thái
     *
     * @param status - AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE
     * @return List<ParkingSlotResponse>
     */
    public List<ParkingSlotResponse> getSlotsByStatus(String status, String requesterEmail) {
        BranchAccess branchAccess = resolveBranchAccess(requesterEmail);
        List<ParkingSlot> slots = branchAccess.restricted()
                ? parkingSlotRepository.findByStatusVisibleByBranchCode(status, branchAccess.branchCode())
                : parkingSlotRepository.findByStatus(status);

        Map<Long, Booking> activeBookings = resolveActiveBookingsBySlotId(slots);

        return slots.stream()
                .map(slot -> convertToResponse(slot, activeBookings.get(slot.getId())))
                .collect(Collectors.toList());
    }

    public ParkingRecommendationResponse recommendSlot(String requestedType, String requesterEmail) {
        String normalizedType = normalizeVehicleType(requestedType);
        BranchAccess branchAccess = resolveBranchAccess(requesterEmail);

        List<ParkingSlot> availableSlots = branchAccess.restricted()
                ? parkingSlotRepository.findByStatusVisibleByBranchCode("AVAILABLE", branchAccess.branchCode())
                : parkingSlotRepository.findByStatusOrderBySlotNameAsc("AVAILABLE");
        if (availableSlots.isEmpty()) {
            throw new RuntimeException("Hiện tại không còn slot AVAILABLE để gợi ý.");
        }

        List<ParkingSlot> typeMatchedSlots = normalizedType == null
                ? availableSlots
                : availableSlots.stream()
                        .filter(slot -> normalizedType.equalsIgnoreCase(slot.getType()))
                        .toList();

        List<ParkingSlot> candidateSlots = typeMatchedSlots.isEmpty() ? availableSlots : typeMatchedSlots;

        List<ParkingSlotResponse> rankedSlots = candidateSlots.stream()
                .sorted(Comparator
                        .comparingInt(this::recommendationScore)
                        .thenComparing(ParkingSlot::getPricePerHour, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ParkingSlot::getSlotName))
                .map(this::convertToResponse)
                .toList();

        ParkingSlotResponse recommendedSlot = rankedSlots.get(0);
        List<ParkingSlotResponse> alternativeSlots = rankedSlots.stream()
                .skip(1)
                .limit(3)
                .toList();

        String explanation = typeMatchedSlots.isEmpty() && normalizedType != null
                ? "Không còn slot đúng loại xe, hệ thống fallback sang slot trống gần cổng ra và thang máy nhất."
                : "Ưu tiên slot trống gần cổng ra và thang máy nhất, sau đó mới xét giá và thứ tự bản đồ.";

        return ParkingRecommendationResponse.builder()
                .requestedType(normalizedType != null ? normalizedType : "ANY")
                .recommendedSlot(recommendedSlot)
                .alternativeSlots(alternativeSlots)
                .explanation(explanation)
                .build();
    }

    /**
     * Lấy thông tin 1 slot theo ID
     *
     * @param id - ID của slot
     * @return ParkingSlotResponse
     * @throws RuntimeException nếu không tìm thấy
     */
    public ParkingSlotResponse getSlotById(Long id, String requesterEmail) {
        Long requiredId = Objects.requireNonNull(id, "id không được null");
        BranchAccess branchAccess = resolveBranchAccess(requesterEmail);
        ParkingSlot slot = (branchAccess.restricted()
                ? parkingSlotRepository.findByIdVisibleByBranchCode(requiredId, branchAccess.branchCode())
                : parkingSlotRepository.findById(requiredId))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy slot với ID: " + id));
        Booking active = bookingRepository.findFirstByParkingSlot_IdAndStatusOrderByCheckInTimeDesc(slot.getId(), Booking.BookingStatus.CHECKED_IN)
                .orElse(null);
        return convertToResponse(slot, active);
    }

    /**
     * Tạo slot mới (Chỉ Admin)
     *
     * @param request - Thông tin slot
     * @return ParkingSlotResponse
     * @throws RuntimeException nếu slotName đã tồn tại
     */
    @Transactional
    @SuppressWarnings("null")
    public ParkingSlotResponse createSlot(ParkingSlotRequest request, String requesterEmail) {
        // Kiểm tra trùng tên slot
        if (parkingSlotRepository.findBySlotName(request.getSlotName()).isPresent()) {
            throw new RuntimeException("Slot " + request.getSlotName() + " đã tồn tại");
        }

        String branchCode = resolveWritableBranch(request.getBranchCode(), requesterEmail, DEFAULT_BRANCH);

        // Tạo mới
        ParkingSlot slot = ParkingSlot.builder()
                .slotName(request.getSlotName())
                .type(request.getType())
                .status(request.getStatus())
                .pricePerHour(request.getPricePerHour())
                .branchCode(branchCode)
                .build();

        ParkingSlot savedSlot = Objects.requireNonNull(parkingSlotRepository.save(slot));
        ParkingSlotResponse response = convertToResponse(savedSlot);
        // [FIX 3 - Tech #5 WebSocket] Real-time: Thông báo slot mới được tạo
        webSocketController.sendSlotUpdate(response);
        return response;
    }

    /**
     * Cập nhật slot (Chỉ Admin)
     *
     * @param id - ID của slot cần update
     * @param request - Dữ liệu mới
     * @return ParkingSlotResponse
     * @throws RuntimeException nếu không tìm thấy
     */
    @Transactional
    public ParkingSlotResponse updateSlot(Long id, ParkingSlotRequest request, String requesterEmail) {
        Long requiredId = Objects.requireNonNull(id, "id không được null");
        ParkingSlot slot = parkingSlotRepository.findById(requiredId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy slot với ID: " + id));
        enforceBranchAccess(slot, requesterEmail);

        // Kiểm tra nếu đổi tên slot mà tên mới đã tồn tại
        if (!slot.getSlotName().equals(request.getSlotName())
                && parkingSlotRepository.findBySlotName(request.getSlotName()).isPresent()) {
            throw new RuntimeException("Slot " + request.getSlotName() + " đã tồn tại");
        }

        // Cập nhật thông tin
        slot.setSlotName(request.getSlotName());
        slot.setType(request.getType());
        slot.setStatus(request.getStatus());
        slot.setPricePerHour(request.getPricePerHour());
        slot.setBranchCode(resolveWritableBranch(request.getBranchCode(), requesterEmail, slot.getBranchCode()));

        ParkingSlot updatedSlot = parkingSlotRepository.save(slot);
        ParkingSlotResponse response = convertToResponse(updatedSlot);
        // [FIX 3 - Tech #5 WebSocket] Real-time: Thông báo slot vừa được cập nhật
        webSocketController.sendSlotUpdate(response);
        return response;
    }

    /**
     * Xóa slot (Chỉ Admin)
     *
     * @param id - ID của slot cần xóa
     * @throws RuntimeException nếu không tìm thấy
     */
    @Transactional
    public void deleteSlot(Long id, String requesterEmail) {
        // [CU - Đã thay bằng findById để lấy thông tin gửi WebSocket]
        // if (!parkingSlotRepository.existsById(id)) {
        //     throw new RuntimeException("Không tìm thấy slot với ID: " + id);
        // }
        // parkingSlotRepository.deleteById(id);

        // [FIX 3 - Tech #5 WebSocket] Lấy slot trước khi xóa để gửi WS notification
        Long requiredId = Objects.requireNonNull(id, "id không được null");
        ParkingSlot slot = parkingSlotRepository.findById(requiredId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy slot với ID: " + id));
        enforceBranchAccess(slot, requesterEmail);

        parkingSlotRepository.deleteById(requiredId);

        // Broadcast trạng thái "DELETED" để Frontend xóa slot khỏi bản đồ ngậy lập tức
        webSocketController.sendSlotUpdate(ParkingSlotResponse.builder()
                .id(slot.getId())
                .slotName(slot.getSlotName())
                .type(slot.getType())
                .status("DELETED")
                .pricePerHour(slot.getPricePerHour())
                .branchCode(normalizeBranchCode(slot.getBranchCode()))
                .version(slot.getVersion())
                .build());
    }

    /**
     * Hàm helper: Convert Entity sang DTO Response
     */
    private ParkingSlotResponse convertToResponse(ParkingSlot slot) {
        return convertToResponse(slot, null);
    }

    private ParkingSlotResponse convertToResponse(ParkingSlot slot, Booking activeBooking) {
        Booking active = ("OCCUPIED".equalsIgnoreCase(slot.getStatus())) ? activeBooking : null;
        return ParkingSlotResponse.builder()
                .id(slot.getId())
                .slotName(slot.getSlotName())
                .type(slot.getType())
                .status(slot.getStatus())
                .pricePerHour(slot.getPricePerHour())
                .branchCode(normalizeBranchCode(slot.getBranchCode()))
                .activeBookingId(active != null ? active.getId() : null)
                .activeVehiclePlate(active != null ? active.getVehiclePlate() : null)
                .activeCheckInTime(active != null ? active.getCheckInTime() : null)
                .version(slot.getVersion())
                .build();
    }

    private Map<Long, Booking> resolveActiveBookingsBySlotId(List<ParkingSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return Map.of();
        }

        List<Long> slotIds = slots.stream()
                .map(ParkingSlot::getId)
                .filter(Objects::nonNull)
                .toList();

        if (slotIds.isEmpty()) {
            return Map.of();
        }

        List<Booking> actives = bookingRepository.findByParkingSlot_IdInAndStatus(slotIds, Booking.BookingStatus.CHECKED_IN);
        if (actives == null || actives.isEmpty()) {
            return Map.of();
        }

        Map<Long, Booking> result = new HashMap<>();
        for (Booking booking : actives) {
            if (booking == null || booking.getParkingSlot() == null || booking.getParkingSlot().getId() == null) {
                continue;
            }
            Long slotId = booking.getParkingSlot().getId();
            Booking existing = result.get(slotId);
            if (existing == null) {
                result.put(slotId, booking);
                continue;
            }
            LocalDateTime a = booking.getCheckInTime();
            LocalDateTime b = existing.getCheckInTime();
            if (b == null || (a != null && a.isAfter(b))) {
                result.put(slotId, booking);
            }
        }
        return result;

    }

    private BranchAccess resolveBranchAccess(String requesterEmail) {
        if (requesterEmail == null || requesterEmail.isBlank()) {
            return BranchAccess.unrestricted();
        }

        return userRepository.findByEmail(requesterEmail)
                .filter(User::isAdmin)
                .map(user -> {
                    if (!user.isBranchAdmin()) {
                        return BranchAccess.unrestricted();
                    }
                    return new BranchAccess(normalizeBranchCode(user.getBranchCode()), true);
                })
                .orElse(BranchAccess.unrestricted());
    }

    private void enforceBranchAccess(ParkingSlot slot, String requesterEmail) {
        BranchAccess branchAccess = resolveBranchAccess(requesterEmail);
        if (branchAccess.restricted() && !branchAccess.branchCode().equals(normalizeBranchCode(slot.getBranchCode()))) {
            throw new RuntimeException("Admin chi nhánh chỉ được thao tác với slot thuộc chi nhánh của mình.");
        }
    }

    private String resolveWritableBranch(String requestedBranchCode, String requesterEmail, String fallbackBranchCode) {
        BranchAccess branchAccess = resolveBranchAccess(requesterEmail);
        if (branchAccess.restricted()) {
            if (requestedBranchCode != null && !requestedBranchCode.isBlank()
                    && !branchAccess.branchCode().equals(normalizeBranchCode(requestedBranchCode))) {
                throw new RuntimeException("Admin chi nhánh không được gán slot sang chi nhánh khác.");
            }
            return branchAccess.branchCode();
        }

        if (requestedBranchCode == null || requestedBranchCode.isBlank()) {
            return normalizeBranchCode(fallbackBranchCode);
        }
        return normalizeBranchCode(requestedBranchCode);
    }

    private String normalizeBranchCode(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) {
            return DEFAULT_BRANCH;
        }
        return branchCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeVehicleType(String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return null;
        }

        String normalized = requestedType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("SEDAN", "SUV").contains(normalized)) {
            throw new RuntimeException("Loại xe gợi ý chỉ hỗ trợ SEDAN hoặc SUV.");
        }
        return normalized;
    }

    private int recommendationScore(ParkingSlot slot) {
        SlotCoordinate coordinate = parseCoordinate(slot.getSlotName());
        int exitDistance = Math.min(
                manhattanDistance(coordinate, 0, 0),
                manhattanDistance(coordinate, 0, 3));
        int elevatorDistance = Math.min(
                manhattanDistance(coordinate, 1, 1),
                manhattanDistance(coordinate, 2, 1));

        return (exitDistance * EXIT_WEIGHT)
                + (elevatorDistance * ELEVATOR_WEIGHT);
    }

    private SlotCoordinate parseCoordinate(String slotName) {
        if (slotName == null || slotName.isBlank()) {
            return new SlotCoordinate(99, 99);
        }

        String normalized = slotName.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() < 2 || !Character.isLetter(normalized.charAt(0))) {
            return new SlotCoordinate(99, 99);
        }

        int row = normalized.charAt(0) - 'A';
        try {
            int column = Integer.parseInt(normalized.substring(1)) - 1;
            return new SlotCoordinate(Math.max(row, 0), Math.max(column, 0));
        } catch (NumberFormatException exception) {
            return new SlotCoordinate(99, 99);
        }
    }

    private int manhattanDistance(SlotCoordinate coordinate, int targetRow, int targetColumn) {
        return Math.abs(coordinate.row() - targetRow) + Math.abs(coordinate.column() - targetColumn);
    }

    private record SlotCoordinate(int row, int column) {

    }

    private record BranchAccess(String branchCode, boolean restricted) {

        private static BranchAccess unrestricted() {
            return new BranchAccess(DEFAULT_BRANCH, false);
        }
    }
}
