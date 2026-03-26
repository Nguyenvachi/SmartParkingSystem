# Flutter Mobile App (Plan)

Mục tiêu: app Flutter cho End-User (Khách gửi xe) với 4 module: Auth, Dashboard, Booking, Profile.

## 1) Khởi tạo project

```bash
flutter create smartparking_mobile
cd smartparking_mobile
```

Cấu hình base URL (dev/prod):
- Dev (Docker local): `http://10.0.2.2:8080` (Android emulator)
- VPS: `http://<domain>:8080`

## 2) Cấu trúc thư mục đề xuất (lib/)

```text
lib/
  core/
    config/
      api_config.dart
    http/
      api_client.dart
      auth_interceptor.dart
    storage/
      token_storage.dart
    models/
  features/
    auth/
      data/
      ui/
    dashboard/
      ui/
    booking/
      data/
      ui/
    profile/
      data/
      ui/
  main.dart
```

## 3) Mapping API (Backend Spring Boot hiện có)

### Auth
- `POST /api/auth/register`
- `POST /api/auth/login`
- (optional) `POST /api/auth/forgot-password`
- (optional) `POST /api/auth/reset-password`

### Slots (Dashboard)
- `GET /api/slots`
- `GET /api/slots?status=AVAILABLE`
- `GET /api/slots/recommendation?vehicleType=SEDAN`

### Booking
- `POST /api/bookings` (JWT required)
- `GET /api/bookings` (JWT required)
- `GET /api/bookings/{id}` (JWT required)
- `POST /api/bookings/{id}/checkin` (JWT required)
- `POST /api/bookings/{id}/checkout` (JWT required)
- `DELETE /api/bookings/{id}` (JWT required)

### Wallet (Profile)
- `GET /api/wallet`
- `GET /api/wallet/transactions`
- `POST /api/wallet/top-up`
- `POST /api/wallet/withdraw`

## 4) Luồng tối thiểu cho End-User

1. Login -> lưu JWT (secure storage)
2. Dashboard -> load danh sách slot AVAILABLE
3. Booking -> tạo booking, hiển thị QR từ `qrCodeBase64` (base64 PNG)
4. Profile -> xem số dư ví và lịch sử giao dịch

## 5) Checklist kết nối API nhanh

- Thêm header `Authorization: Bearer <token>` cho các API cần JWT.
- Với emulator Android, gọi backend local Docker qua `10.0.2.2:8080`.
- Nếu deploy VPS, cấu hình CORS ở `.env` theo domain frontend/mobile origin (xem [DEPLOYMENT.md](../DEPLOYMENT.md)).
