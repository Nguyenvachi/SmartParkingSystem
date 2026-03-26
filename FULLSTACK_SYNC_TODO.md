# Fullstack Sync TODO (BE/FE/Mobile)

Mục tiêu: đồng bộ 3 tầng để demo end-to-end mượt (Admin tạo slot -> User đặt chỗ -> QR -> check-in -> checkout trừ ví -> realtime map).

## Trạng thái hiện tại (đã quét)

- Backend: đủ Auth/Slots/Booking/Wallet/Payment gateway, booking đã có 3 rule Phase 1.
- Frontend (Web): đã có login/dashboard/booking/QR/websocket/wallet.
- Mobile: mới có plan; cần scaffold app Flutter.

## Ưu tiên triển khai (làm theo thứ tự)

### P0 — Mobile MVP (để hoàn tất GD3 trước)

- [ ] Scaffold Flutter app trong `mobile/smartparking_mobile/`.
- [ ] Auth: login -> lưu JWT (secure storage) -> auto login.
- [ ] Dashboard: gọi `GET /api/slots` và hiển thị list/grid đơn giản.
- [ ] Booking:
  - [ ] tạo booking `POST /api/bookings` (slotId + vehiclePlate optional)
  - [ ] list booking `GET /api/bookings`
  - [ ] show QR từ `qrCodeBase64`
  - [ ] check-in `POST /api/bookings/{id}/checkin`
  - [ ] checkout `POST /api/bookings/{id}/checkout` (voucherCode optional)
- [ ] Profile/Wallet: `GET /api/wallet`, `GET /api/wallet/transactions`.

### P1 — Web FE đồng bộ rule/UX

- [ ] Booking: hiển thị rõ lỗi backend (admin blocked / active booking / insufficient wallet) bằng message trả về.
- [ ] Wallet: tránh hard-code minimum top-up (hiện đang check 10.000 VND ở FE).
- [ ] Config: chuẩn hoá `API_BASE_URL` ở 1 nơi (đang dùng cả `API_BASE_URL` và fallback `window.location.origin`).

### P2 — Backend “contract” chuẩn cho FE/Mobile

- [ ] (Tuỳ chọn) Chuẩn hoá error response (thêm `errorCode`) để FE/Mobile map message nhất quán.
- [ ] (Tuỳ chọn) Endpoint config read-only cho FE (min topup, membership fee...).

### P3 — Deploy (GD2 làm cuối)

- [ ] Docker compose + `.env` theo [DEPLOYMENT.md](DEPLOYMENT.md)
- [ ] DNS A record trỏ domain -> VPS
- [ ] Chạy `docker compose up -d --build`

## File quan trọng

- Backend booking rules: backend/src/main/java/com/parking/smartparking/service/BookingService.java
- Web booking flow + QR: frontend/js/main.js
- Web API wrapper: frontend/js/api.js
- Mobile plan: mobile/README.md
- VPS deploy: DEPLOYMENT.md
