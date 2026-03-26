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

# CORS / WebSocket: cho phép domain frontend gọi API
APP_CORS_ALLOWED_ORIGINS=http://smartparking.id.vn,http://www.smartparking.id.vn
APP_WS_ALLOWED_ORIGIN_PATTERNS=http://smartparking.id.vn,http://www.smartparking.id.vn

# (Khuyến nghị) tắt expose token reset password khi deploy thật
APP_PASSWORD_RESET_EXPOSE_TOKEN=false

# (Tuỳ chọn) đổi port public
FRONTEND_PORT=80
BACKEND_PORT=8080
PHPMYADMIN_PORT=8081

# (Tuỳ chọn) database
MYSQL_ROOT_PASSWORD=CHANGE_ME_STRONG
MYSQL_USERNAME=root
MYSQL_PASSWORD=CHANGE_ME_STRONG
MYSQL_PORT=3306

# (Tuỳ chọn) OAuth2 Google
# GOOGLE_CLIENT_ID=
# GOOGLE_CLIENT_SECRET=

# (Khuyến nghị) set secret riêng khi deploy
# APP_JWT_SECRET=... (>= 32 chars)
# APP_QR_SECRET=...
```

Nếu bạn muốn dùng HTTPS (khuyến nghị), bạn sẽ cần reverse proxy (Nginx/Caddy) + SSL (Let’s Encrypt). Phần này không bắt buộc theo TODO hiện tại.

## 5) Run containers

```bash
docker compose up -d --build
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
ufw allow 8080/tcp
ufw allow 8081/tcp
ufw enable
ufw status
```

## 7) URL kiểm tra

- Frontend: `http://<domain-or-ip>/` (nếu `FRONTEND_PORT=80`) hoặc `http://<domain-or-ip>:3000/`
- Backend API: `http://<domain-or-ip>:8080/api`
- phpMyAdmin: `http://<domain-or-ip>:8081/`

## Troubleshooting nhanh

- Không vào được web: kiểm tra security group/firewall của nhà VPS + `docker compose ps`.
- FE gọi API lỗi CORS: kiểm tra `APP_CORS_ALLOWED_ORIGINS` và `APP_WS_ALLOWED_ORIGIN_PATTERNS` trong `.env`, rồi:
  - `docker compose up -d --force-recreate backend`
