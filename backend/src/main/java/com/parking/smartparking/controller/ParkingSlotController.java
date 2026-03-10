package com.parking.smartparking.controller;

import com.parking.smartparking.dto.request.ParkingSlotRequest;
import com.parking.smartparking.dto.response.ParkingSlotResponse;
import com.parking.smartparking.service.ParkingSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API Controller cho Parking Slot Management
 *
 * Base URL: /api/slots
 *
 * Endpoints: - GET /api/slots : Lấy tất cả slots - GET
 * /api/slots?status=AVAILABLE : Lọc theo trạng thái - GET /api/slots/{id} : Lấy
 * 1 slot - POST /api/slots : Tạo slot mới (Admin only) - PUT /api/slots/{id} :
 * Cập nhật slot (Admin only) - DELETE /api/slots/{id} : Xóa slot (Admin only)
 *
 * Tech Key: - Tính năng #1: Concurrency (JPA tự xử lý @Version) - Tính năng #5:
 * WebSocket (Sẽ gửi message khi slot thay đổi)
 */
@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
public class ParkingSlotController {

    private final ParkingSlotService parkingSlotService;

    /**
     * API lấy tất cả slots hoặc lọc theo status
     *
     * Request: GET /api/slots GET /api/slots?status=AVAILABLE
     *
     * Response (200 OK): [ { "id": 1, "slotName": "A01", "type": "SEDAN",
     * "status": "AVAILABLE", "pricePerHour": 5000.00, "version": 0 }, ... ]
     *
     * @param status - (Optional) Filter theo trạng thái
     * @return List<ParkingSlotResponse>
     */
    @GetMapping
    public ResponseEntity<List<ParkingSlotResponse>> getAllSlots(
            @RequestParam(required = false) String status) {
        List<ParkingSlotResponse> slots;

        if (status != null && !status.isEmpty()) {
            slots = parkingSlotService.getSlotsByStatus(status);
        } else {
            slots = parkingSlotService.getAllSlots();
        }

        return ResponseEntity.ok(slots);
    }

    /**
     * API lấy thông tin 1 slot
     *
     * Request: GET /api/slots/1
     *
     * Response (200 OK): { "id": 1, "slotName": "A01", "type": "SEDAN",
     * "status": "AVAILABLE", "pricePerHour": 5000.00, "version": 0 }
     *
     * @param id - ID của slot
     * @return ParkingSlotResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<ParkingSlotResponse> getSlotById(@PathVariable Long id) {
        ParkingSlotResponse slot = parkingSlotService.getSlotById(id);
        return ResponseEntity.ok(slot);
    }

    /**
     * API tạo slot mới (Chỉ Admin)
     *
     * Request: POST /api/slots Content-Type: application/json Body: {
     * "slotName": "A01", "type": "SEDAN", "status": "AVAILABLE",
     * "pricePerHour": 5000.00 }
     *
     * Response (201 CREATED): { "id": 1, "slotName": "A01", ... }
     *
     * @param request - Thông tin slot (đã validate)
     * @return ParkingSlotResponse
     */
    @PostMapping
    public ResponseEntity<ParkingSlotResponse> createSlot(
            @Valid @RequestBody ParkingSlotRequest request) {
        ParkingSlotResponse createdSlot = parkingSlotService.createSlot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSlot);
    }

    /**
     * API cập nhật slot (Chỉ Admin)
     *
     * Request: PUT /api/slots/1 Content-Type: application/json Body: {
     * "slotName": "A01", "type": "SUV", "status": "MAINTENANCE",
     * "pricePerHour": 7000.00 }
     *
     * Response (200 OK): { "id": 1, "slotName": "A01", "type": "SUV", ... }
     *
     * @param id - ID của slot cần update
     * @param request - Dữ liệu mới (đã validate)
     * @return ParkingSlotResponse
     */
    @PutMapping("/{id}")
    public ResponseEntity<ParkingSlotResponse> updateSlot(
            @PathVariable Long id,
            @Valid @RequestBody ParkingSlotRequest request) {
        ParkingSlotResponse updatedSlot = parkingSlotService.updateSlot(id, request);
        return ResponseEntity.ok(updatedSlot);
    }

    /**
     * API xóa slot (Chỉ Admin)
     *
     * Request: DELETE /api/slots/1
     *
     * Response (204 NO CONTENT)
     *
     * @param id - ID của slot cần xóa
     * @return ResponseEntity<Void>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSlot(@PathVariable Long id) {
        parkingSlotService.deleteSlot(id);
        return ResponseEntity.noContent().build();
    }
}
