# Deploy VPS (Docker Compose)

Mục tiêu: public SmartParkingSystem lên VPS Linux bằng `docker compose`.

## 1) Chuẩn bị VPS + Domain

- VPS Ubuntu 22.04+ (khuyến nghị: 2 vCPU, 2–4GB RAM)
- Domain (vd: `smartparking.id.vn`)

### DNS (tại nhà cung cấp domain)

- Tạo bản ghi **A**:
  - `@` → `<VPS_PUBLIC_IP>`
  - (tuỳ chọn) `www` → `<VPS_PUBLIC_IP>`

Chờ DNS propagate (thường 5–30 phút, đôi khi lâu hơn).

## 2) SSH vào VPS

```bash
ssh root@<VPS_PUBLIC_IP>
```

## 3) Cài Docker + Docker Compose plugin

Ubuntu:

```bash
apt update -y
apt install -y ca-certificates curl gnupg
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo \"$VERSION_CODENAME\") stable" \
  > /etc/apt/sources.list.d/docker.list

apt update -y
apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable --now docker
```

Kiểm tra:

```bash
docker --version
docker compose version
```

## 4) Clone source và cấu hình `.env`

```bash
cd /opt
git clone <YOUR_GIT_URL> smartparking
cd smartparking
```

Tạo file `.env` (cùng cấp với `docker-compose.yml`):

```bash
nano .env
```

Mẫu `.env` tối thiểu:

```env
# Public base URL của frontend (dùng cho link redirect/reset password)
APP_FRONTEND_BASE_URL=http://smartparking.id.vn

# Lưu ý: `.env` của Docker Compose chỉ hỗ trợ `KEY=VALUE`.
# Không đặt các placeholder dạng `${VAR:}` trong `.env` (Compose sẽ báo lỗi).

# Payment gateways (MoMo/VNPay)
# Base URL public (HTTPS) của backend để gateway gọi callback/IPN.
# Khi test local:
# - Android emulator: có thể dùng `http://10.0.2.2:8080` (10.0.2.2 = máy host nhìn từ emulator)
# - Điện thoại thật cùng LAN: dùng `http://<LAN_IP_OF_YOUR_PC>:8080`
# - Nếu gateway cần gọi IPN từ internet: dùng ngrok/cloudflared (HTTPS) và set vào đây.
APP_PAYMENT_BACKEND_BASE_URL=https://api.smartparking.id.vn

# Bật/tắt gateway (mặc định false). Khi bật cần set key tương ứng.
APP_PAYMENT_MOMO_ENABLED=false
# APP_PAYMENT_MOMO_PARTNER_CODE=
# APP_PAYMENT_MOMO_ACCESS_KEY=
# APP_PAYMENT_MOMO_SECRET_KEY=
# APP_PAYMENT_MOMO_ALLOW_UNSAFE_RETURN_SUCCESS=false

APP_PAYMENT_VNPAY_ENABLED=false
# APP_PAYMENT_VNPAY_TMN_CODE=
# APP_PAYMENT_VNPAY_HASH_SECRET=

# Email (Invoice + Forgot/Reset password)
# Bật gửi mail best-effort (không làm fail API nếu SMTP lỗi).
APP_MAIL_ENABLED=false
APP_MAIL_FROM=no-reply@smartparking.id.vn
# Cấu hình SMTP (ví dụ Gmail SMTP)
# SPRING_MAIL_HOST=smtp.gmail.com
# SPRING_MAIL_PORT=587
# SPRING_MAIL_USERNAME=your_email@gmail.com
# SPRING_MAIL_PASSWORD=your_app_password
# SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
# SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true

# Test local nhanh (không cần SMTP thật): dùng Mailpit trong docker-compose
# - Mở inbox: http://localhost:8025
# - Cấu hình .env:
#   APP_MAIL_ENABLED=true
#   APP_MAIL_FROM=no-reply@smartparking.local
#   SPRING_MAIL_HOST=mailpit
#   SPRING_MAIL_PORT=1025

Ghi chú: `.env` chỉ áp dụng khi chạy bằng Docker Compose. Nếu chạy backend local (mvn/IDE), hãy cấu hình mail bằng
`backend/application-secrets.properties` hoặc environment variables của hệ điều hành.

# CORS / WebSocket: cho phép domain frontend gọi API
APP_CORS_ALLOWED_ORIGINS=http://smartparking.id.vn,http://www.smartparking.id.vn
APP_WS_ALLOWED_ORIGIN_PATTERNS=http://smartparking.id.vn,http://www.smartparking.id.vn

# (Khuyến nghị) tắt expose token reset password khi deploy thật
APP_PASSWORD_RESET_EXPOSE_TOKEN=false

# (Tuỳ chọn) đổi port public
FRONTEND_PORT=80

# Khuyến nghị (an toàn hơn): chỉ expose frontend ra public,
# còn backend/phpmyadmin/mysql/mailpit bind vào localhost.
# Frontend Nginx đã proxy sẵn /api và /ws tới backend.
BACKEND_PORT=127.0.0.1:8080
PHPMYADMIN_PORT=127.0.0.1:8081
MYSQL_PORT=127.0.0.1:3306
MAILPIT_SMTP_PORT=127.0.0.1:1025
MAILPIT_WEB_PORT=127.0.0.1:8025

# (Tuỳ chọn) database
MYSQL_ROOT_PASSWORD=root
MYSQL_USERNAME=root
MYSQL_PASSWORD=root

# (Tuỳ chọn) OAuth2 Google
# GOOGLE_CLIENT_ID=
# GOOGLE_CLIENT_SECRET=

# (Khuyến nghị) set secret riêng khi deploy
# APP_JWT_SECRET=... (>= 32 chars)
# APP_QR_SECRET=...
```

Nếu bạn deploy theo HTTPS ngay (khuyến nghị), bạn có thể copy template sẵn trong repo:

```bash
cp .env.vps.example .env
nano .env
```

### `.env` gợi ý cho production (smartparking.id.vn + www, HTTPS, OAuth + Payment)

```env
# Domain
APP_FRONTEND_BASE_URL=https://smartparking.id.vn
APP_CORS_ALLOWED_ORIGINS=https://smartparking.id.vn,https://www.smartparking.id.vn
APP_WS_ALLOWED_ORIGIN_PATTERNS=https://smartparking.id.vn,https://www.smartparking.id.vn

# Expose only reverse proxy (HTTPS)
PROXY_HTTP_PORT=80
PROXY_HTTPS_PORT=443

# Keep internal services bound to localhost (safer)
FRONTEND_PORT=127.0.0.1:3000
BACKEND_PORT=127.0.0.1:8080
PHPMYADMIN_PORT=127.0.0.1:8081
MYSQL_PORT=127.0.0.1:3306
MAILPIT_WEB_PORT=127.0.0.1:8025
MAILPIT_SMTP_PORT=127.0.0.1:1025

# Security
APP_PASSWORD_RESET_EXPOSE_TOKEN=false
APP_JWT_SECRET=CHANGE_ME_32PLUS_CHARS
APP_QR_SECRET=CHANGE_ME_32PLUS_CHARS

# DB
MYSQL_ROOT_PASSWORD=CHANGE_ME_STRONG
MYSQL_USERNAME=root
MYSQL_PASSWORD=CHANGE_ME_STRONG

# Google OAuth
GOOGLE_CLIENT_ID=CHANGE_ME
GOOGLE_CLIENT_SECRET=CHANGE_ME

# Payment callbacks must be public HTTPS
APP_PAYMENT_BACKEND_BASE_URL=https://smartparking.id.vn

APP_PAYMENT_MOMO_ENABLED=false
APP_PAYMENT_MOMO_PARTNER_CODE=CHANGE_ME
APP_PAYMENT_MOMO_ACCESS_KEY=CHANGE_ME
APP_PAYMENT_MOMO_SECRET_KEY=CHANGE_ME
APP_PAYMENT_MOMO_ALLOW_UNSAFE_RETURN_SUCCESS=false

APP_PAYMENT_VNPAY_ENABLED=false
APP_PAYMENT_VNPAY_TMN_CODE=CHANGE_ME
APP_PAYMENT_VNPAY_HASH_SECRET=CHANGE_ME

# (Optional) email
APP_MAIL_ENABLED=false
APP_MAIL_FROM=no-reply@smartparking.id.vn
```

Gợi ý tạo secret nhanh trên VPS:

```bash
openssl rand -hex 32
```

Khuyến nghị: khi deploy VPS, không dùng `backend/application-secrets.properties` (dễ lộ secrets). Hãy set qua `.env`/environment variables.

HTTPS (khuyến nghị): repo đã có sẵn reverse proxy Nginx + Let's Encrypt (Certbot) trong Docker Compose (profile `https`) và script init cert ở `scripts/vps/init-letsencrypt.sh`.

## 5) Run containers

```bash
docker compose up -d --build
```

### HTTPS (Nginx + Let's Encrypt via Docker Compose)

Repo đã có sẵn cấu hình profile `https` + script init cert.

1) Mở firewall/security group cho 80 và 443.
2) DNS đã trỏ về VPS.
3) Chạy:

```bash
chmod +x scripts/vps/init-letsencrypt.sh
export LETSENCRYPT_EMAIL=admin@smartparking.id.vn

# Nếu trước đó bạn đã chạy mode HTTP (port 80), hãy dừng để giải phóng port 80:
docker compose down --remove-orphans

# Obtain cert (standalone on :80), then start proxy + app stack + auto-renew
./scripts/vps/init-letsencrypt.sh
```

Xem trạng thái:

```bash
docker compose ps
```

Logs nhanh:

```bash
docker compose logs -f --tail=200 backend
```

## 6) Mở port firewall (nếu có)

Nếu VPS bật UFW:

```bash
ufw allow 22/tcp
ufw allow 80/tcp
## Nếu bạn bind backend/phpmyadmin/mysql/mailpit về localhost (khuyến nghị),
## KHÔNG cần mở 8080/8081/3306/8025 ra public.
ufw enable
ufw status
```

## 7) URL kiểm tra

- Frontend: `http://<domain-or-ip>/` (nếu `FRONTEND_PORT=80`) hoặc `http://<domain-or-ip>:3000/`
- API (qua frontend proxy): `http://<domain-or-ip>/api`
- WebSocket (qua frontend proxy): `http://<domain-or-ip>/ws`
- Backend API trực tiếp: `http://<domain-or-ip>:8080/api` (chỉ khi bạn expose port 8080)
- phpMyAdmin: `http://<domain-or-ip>:8081/` (chỉ khi bạn expose port 8081)

## Troubleshooting nhanh

- Không vào được web (ERR_CONNECTION_REFUSED):
  - Kiểm tra firewall/security group (nhà VPS) đã mở **80/443** chưa.
  - Trên VPS chạy `docker compose ps` để xem service nào đang publish port:
    - Nếu bạn chạy HTTP thường (không profile `https`): mặc định frontend publish **:3000** → hãy thử `http://<domain>:3000/`, hoặc set `FRONTEND_PORT=80` trong `.env` rồi `docker compose down && docker compose up -d --build`.
    - Nếu bạn dùng `.env.vps.example`: frontend/backend bị bind vào `127.0.0.1` (không public) → cần chạy HTTPS proxy theo hướng dẫn ở mục HTTPS (`./scripts/vps/init-letsencrypt.sh`, profile `https`).
- FE gọi API lỗi CORS: kiểm tra `APP_CORS_ALLOWED_ORIGINS` và `APP_WS_ALLOWED_ORIGIN_PATTERNS` trong `.env`, rồi:
  - `docker compose up -d --force-recreate backend`

- Backend bị 502 (Bad Gateway) khi gọi `/api` hoặc `/oauth2/*`:
  - Thường là backend container bị exit do DB password sai.
  - Nếu bạn đổi `MYSQL_ROOT_PASSWORD` trong `.env` thì đảm bảo backend dùng đúng password đó (và nếu vẫn giữ `MYSQL_USERNAME=root`, hãy set `MYSQL_PASSWORD` trùng `MYSQL_ROOT_PASSWORD` để khỏi nhầm).
  - Nếu bạn đã chạy MySQL trước đó rồi mới đổi `MYSQL_ROOT_PASSWORD`: MySQL sẽ *không* tự đổi password trong data volume. Cách nhanh nhất:
    - `docker compose down -v` (xoá volume DB, dữ liệu sẽ mất) rồi `docker compose up -d --build`
    - Hoặc reset password thủ công trong MySQL container.
  - Kiểm tra nhanh: `docker compose ps` + `docker compose logs --tail=200 backend`.

## Google OAuth Redirect URI (production)

Trong Google Cloud Console → OAuth client:

- Authorized JavaScript origins:
  - `https://smartparking.id.vn`
  - `https://www.smartparking.id.vn`
- Authorized redirect URIs:
  - `https://smartparking.id.vn/login/oauth2/code/google`
  - `https://www.smartparking.id.vn/login/oauth2/code/google`

## Payment callback URLs (production)

Nếu gateway yêu cầu whitelist callback, dùng (HTTPS):

- MoMo:
  - Return: `https://smartparking.id.vn/api/payments/callback/momo/return`
  - IPN: `https://smartparking.id.vn/api/payments/callback/momo/ipn`
- VNPay:
  - Return: `https://smartparking.id.vn/api/payments/callback/vnpay/return`
  - IPN: `https://smartparking.id.vn/api/payments/callback/vnpay/ipn`
