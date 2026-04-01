# smartparking_mobile

Flutter app for SmartParking (Auth, Dashboard, Booking, Profile).

## Prerequisites

- Flutter SDK installed
- Backend running (recommended via Docker Compose)

## Run backend + web locally (recommended)

From repo root:

```bash
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api

## Run mobile

From `mobile/smartparking_mobile`:

```bash
flutter pub get
```

### Android Emulator

Android emulator must use host loopback via `10.0.2.2`:

```bash
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api
```

### Chrome (Web)

```bash
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080/api
```

### Windows desktop

```bash
flutter run -d windows --dart-define=API_BASE_URL=http://localhost:8080/api
```

### Physical phone (same Wi‑Fi)

Use your PC LAN IP (example):

```bash
flutter run --dart-define=API_BASE_URL=http://192.168.1.10:8080/api
```

## Quick test checklist

1) Create account / login (web at http://localhost:3000 or mobile)
2) Ensure wallet has balance before creating booking
	- Quick way: use web dashboard “Nạp tiền” (gateway) OR call `POST /api/wallet/top-up` (requires JWT)
3) On mobile:
	- Dashboard: load slots
	- Booking: create booking → verify QR shows → try check-in

## Notes

- API base is read from `--dart-define=API_BASE_URL=...` (see `lib/core/config/api_config.dart`).
- Booking creation will fail if wallet balance < slot price per hour (backend rule).
