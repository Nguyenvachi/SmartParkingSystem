# SmartParkingSystem
J2EE &amp; Tester

## Run with Docker (recommended)

Prerequisites: Docker Desktop.

```bash
docker compose up --build
```

## Email (Gmail SMTP) notes

- Docker Compose sẽ tự load file `.env` ở root.
- Khi chạy backend local bằng `mvnw.cmd spring-boot:run` / IDE, backend cũng sẽ tự đọc `.env` (best-effort) để map `SPRING_MAIL_*` → `spring.mail.*`.
	- Cách khuyến nghị (an toàn hơn): cấu hình mail trong `backend/application-secrets.properties` (file này đã được ignore bởi Git),
		hoặc set environment variables khi chạy.

## Deploy on VPS

See [DEPLOYMENT.md](DEPLOYMENT.md) for a copy-paste VPS + DNS + `.env` checklist.

- Frontend: http://localhost:3000 (clean URLs: `/login`, `/dashboard`)
- Backend API: http://localhost:8080/api
- MySQL: localhost:3306 (db: `smartparking_db`, user: `root`, pass: `root`)
- phpMyAdmin: http://localhost:8081 (user: `root`, pass: `root`, server/host: `mysql`)

### Google Login (OAuth2)

If you see Google error `401: invalid_client` after switching to Docker, set these in a root `.env` file (same folder as `docker-compose.yml`):

```env
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
```

Then apply the change (recreate backend):

```bash
docker compose up -d --force-recreate backend
```

### Notes

- Password reset demo mode is enabled by default in compose via `APP_PASSWORD_RESET_EXPOSE_TOKEN=true`.
- To disable demo token exposure when deploying: set `APP_PASSWORD_RESET_EXPOSE_TOKEN=false`.

## Gate / barrier flow (Guard-controlled)

- User (mobile/web) chỉ đặt chỗ và xuất trình QR.
- Check-in và Check-out chỉ thực hiện tại cổng (bảo vệ) qua `scanner.html`.
- Backend đã chặn user thường gọi API `POST /api/bookings/{id}/checkin|checkout` (chỉ `ROLE_ADMIN`/`ROLE_BRANCH_ADMIN`).

## End-to-end test script

1) Start stack: `docker compose up --build`
2) Login user (ROLE_USER) trên web hoặc mobile
3) Create booking (status `PENDING`) và mở QR
4) Login staff (ROLE_ADMIN / ROLE_BRANCH_ADMIN) trên web
5) Open `http://localhost:3000/scanner.html`
6) Mode `Check-in` → scan QR → booking chuyển `CHECKED_IN`, slot `OCCUPIED`
7) Mode `Check-out` → scan lại QR → booking chuyển `COMPLETED`, slot `AVAILABLE` (ví bị trừ nếu có phí)
