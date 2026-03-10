package com.parking.smartparking.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.smartparking.controller.WebSocketController;
import com.parking.smartparking.dto.request.ParkingSlotRequest;
import com.parking.smartparking.dto.response.ParkingRecommendationResponse;
import com.parking.smartparking.dto.response.ParkingSlotResponse;
import com.parking.smartparking.entity.ParkingSlot;
import com.parking.smartparking.repository.ParkingSlotRepository;

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

    private final ParkingSlotRepository parkingSlotRepository;
    // [FIX 3 - Tech #5 WebSocket] Inject để gửi real-time update khi slot thay đổi
    private final WebSocketController webSocketController;

    /**
     * Lấy tất cả parking slots
     *
     * @return List<ParkingSlotResponse>
     */
    public List<ParkingSlotResponse> getAllSlots() {
        return parkingSlotRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách slot theo trạng thái
     *
     * @param status - AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE
     * @return List<ParkingSlotResponse>
     */
    public List<ParkingSlotResponse> getSlotsByStatus(String status) {
        return parkingSlotRepository.findByStatus(status).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

        public ParkingRecommendationResponse recommendSlot(String requestedType) {
        String normalizedType = normalizeVehicleType(requestedType);

        List<ParkingSlot> availableSlots = parkingSlotRepository.findByStatusOrderBySlotNameAsc("AVAILABLE");
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
                .thenComparing(ParkingSlot::getSlotName))
            .map(this::convertToResponse)
            .toList();

        ParkingSlotResponse recommendedSlot = rankedSlots.get(0);
        List<ParkingSlotResponse> alternativeSlots = rankedSlots.stream()
            .skip(1)
            .limit(3)
            .toList();

        String explanation = typeMatchedSlots.isEmpty() && normalizedType != null
            ? "Không còn slot cùng loại xe, hệ thống đề xuất slot trống gần cổng nhất còn lại."
            : "Ưu tiên slot trống gần cổng ra/thang máy nhất theo thứ tự bản đồ và đúng loại xe nếu có.";

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
    public ParkingSlotResponse getSlotById(Long id) {
        ParkingSlot slot = parkingSlotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy slot với ID: " + id));
        return convertToResponse(slot);
    }

    /**
     * Tạo slot mới (Chỉ Admin)
     *
     * @param request - Thông tin slot
     * @return ParkingSlotResponse
     * @throws RuntimeException nếu slotName đã tồn tại
     */
    @Transactional
    public ParkingSlotResponse createSlot(ParkingSlotRequest request) {
        // Kiểm tra trùng tên slot
        if (parkingSlotRepository.findBySlotName(request.getSlotName()).isPresent()) {
            throw new RuntimeException("Slot " + request.getSlotName() + " đã tồn tại");
        }

        // Tạo mới
        ParkingSlot slot = ParkingSlot.builder()
                .slotName(request.getSlotName())
                .type(request.getType())
                .status(request.getStatus())
                .pricePerHour(request.getPricePerHour())
                .build();

        ParkingSlot savedSlot = parkingSlotRepository.save(slot);
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
    public ParkingSlotResponse updateSlot(Long id, ParkingSlotRequest request) {
        ParkingSlot slot = parkingSlotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy slot với ID: " + id));

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
    public void deleteSlot(Long id) {
        // [CU - Đã thay bằng findById để lấy thông tin gửi WebSocket]
        // if (!parkingSlotRepository.existsById(id)) {
        //     throw new RuntimeException("Không tìm thấy slot với ID: " + id);
        // }
        // parkingSlotRepository.deleteById(id);

        // [FIX 3 - Tech #5 WebSocket] Lấy slot trước khi xóa để gửi WS notification
        ParkingSlot slot = parkingSlotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy slot với ID: " + id));

        parkingSlotRepository.deleteById(id);

        // Broadcast trạng thái "DELETED" để Frontend xóa slot khỏi bản đồ ngậy lập tức
        webSocketController.sendSlotUpdate(ParkingSlotResponse.builder()
                .id(slot.getId())
                .slotName(slot.getSlotName())
                .type(slot.getType())
                .status("DELETED")
                .pricePerHour(slot.getPricePerHour())
                .version(slot.getVersion())
                .build());
    }

    /**
     * Hàm helper: Convert Entity sang DTO Response
     */
    private ParkingSlotResponse convertToResponse(ParkingSlot slot) {
        return ParkingSlotResponse.builder()
                .id(slot.getId())
                .slotName(slot.getSlotName())
                .type(slot.getType())
                .status(slot.getStatus())
                .pricePerHour(slot.getPricePerHour())
                .version(slot.getVersion())
                .build();
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
        String slotName = slot.getSlotName().toUpperCase(Locale.ROOT);
        int rowScore = slotName.charAt(0) - 'A';
        int columnScore = Integer.parseInt(slotName.substring(1));
        return (rowScore * 10) + columnScore;
    }
}
