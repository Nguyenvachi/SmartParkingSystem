package com.parking.smartparking.controller;

import com.parking.smartparking.dto.response.ParkingSlotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Controller xử lý gửi message qua WebSocket Tech Key: Tính năng #5 - Real-time
 * Map Update
 *
 * Cách sử dụng: 1. Inject WebSocketController vào Service cần gửi update 2. Gọi
 * sendSlotUpdate() khi slot thay đổi 3. Frontend sẽ nhận message và update UI
 *
 * Flow: - User A đặt slot A01 - ParkingSlotService.updateSlot() được gọi -
 * Service gọi webSocketController.sendSlotUpdate(slotResponse) - Message được
 * broadcast đến topic /topic/parking-updates - Tất cả clients đang xem
 * dashboard nhận được update - Frontend đổi màu slot A01 từ Xanh sang Đỏ ngay
 * lập tức
 */

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    /**
     * SimpMessagingTemplate: Spring bean để gửi message qua STOMP
     */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Gửi thông báo cập nhật slot qua WebSocket
     * 
     * @param slotResponse - Thông tin slot đã thay đổi
     * 
     * Message format:
     * {
     *   "id": 1,
     *   "slotName": "A01",
     *   "status": "OCCUPIED",
     *   "version": 1
     * }
     */
    public void sendSlotUpdate(ParkingSlotResponse slotResponse) {
        messagingTemplate.convertAndSend("/topic/parking-updates", slotResponse);
    }

    /**
     * Gửi thông báo tất cả slots (Broadcast toàn bộ map)
     * Sử dụng khi cần refresh toàn bộ UI
     * 
     * @param message - Thông điệp tùy chỉnh
     */
    public void broadcastMessage(String message) {
        messagingTemplate.convertAndSend("/topic/parking-updates", message);
    }
}
