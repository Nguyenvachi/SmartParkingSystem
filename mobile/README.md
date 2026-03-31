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
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `POST /api/auth/login/google` (Mobile Google Sign-In: gửi `idToken`)

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

## 6) Auth module flow (ROLE_USER only)

Mobile app chỉ dành cho End-User (ROLE_USER).

- Nếu đăng nhập ra role khác `ROLE_USER` → app sẽ chặn và không lưu session.

## 7) Test checklist (Auth)

### Email/Password
1) Mở app → Login
2) Nhập email/password hợp lệ → vào app (token lưu secure storage)
3) Kill app + mở lại → vẫn đăng nhập (còn token)
4) Nhập sai password → hiển thị message lỗi từ backend

### Forgot/Reset password (dev/demo)
1) Ở Login → bấm "Quên mật khẩu?" → nhập email
2) Backend trả `resetToken` (nếu bật `APP_PASSWORD_RESET_EXPOSE_TOKEN=true`) → copy token
3) Vào màn "Đặt lại" → nhập token + mật khẩu mới + confirm → thành công
4) Quay lại Login → đăng nhập bằng mật khẩu mới

### Google Sign-In
1) Ở Login → bấm "Đăng nhập bằng Google"
2) Chọn Google account → app lấy `idToken` và gọi `POST /api/auth/login/google`
3) Backend trả JWT → app vào màn chính

Ghi chú:
- Android cần cấu hình đúng SHA-1/SHA-256 + Android OAuth client.
- Khi chạy app, cần truyền Web Client ID làm `serverClientId` để lấy `idToken` đúng audience:
  `flutter run --dart-define=GOOGLE_SERVER_CLIENT_ID=<WEB_CLIENT_ID>.apps.googleusercontent.com`
