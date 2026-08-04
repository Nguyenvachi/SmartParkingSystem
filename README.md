# Smart Parking System

## Overview

Smart Parking System is an academic full-stack project for reserving parking slots and managing gate check-in/check-out. It combines a Spring Boot API, a static web client, and a Flutter end-user application, with Docker Compose for a reproducible local environment.

The OCR screen is explicitly a simulation: it derives a mock plate result from the uploaded file name. It is not an OCR model or a production license-plate recognition service.

## Key Features

- Email/password authentication, JWT authorization, password reset, and optional Google sign-in.
- Parking-slot browsing and recommendation by vehicle type.
- Booking creation, cancellation, scheduled expiry, pricing, and booking history.
- HMAC-signed QR tickets with gate check-in/check-out restricted to administrative roles.
- Wallet transactions, vouchers, membership rules, invoices, and optional sandbox payment adapters.
- User vehicle/profile management, blacklists, audit logging, and WebSocket updates.
- Static responsive web interface for users and gate staff.
- Flutter client for authentication, slots, bookings, wallet information, and profiles.

## Tech Stack

| Area | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA |
| Authentication | JWT, optional Google OAuth 2.0 |
| Database | MySQL 8; H2 for automated tests |
| Web client | HTML, CSS, JavaScript, Bootstrap, Nginx |
| Mobile client | Flutter, Dart, secure storage |
| Supporting libraries | ZXing QR code generation, WebSocket, Spring Mail |
| Local environment | Docker Compose, Mailpit, optional phpMyAdmin |

## Project Structure

```text
SmartParkingSystem/
├── backend/                    Spring Boot REST API and tests
├── frontend/                   Static web client and gate scanner
├── mobile/smartparking_mobile/ Flutter end-user application
├── infra/                      Optional reverse-proxy configuration
└── docker-compose.yml          Local multi-service environment
```

## Prerequisites

For the complete local stack:

- Docker Desktop with Docker Compose

For running components separately:

- Java 21
- MySQL 8
- Flutter with a Dart 3-compatible SDK

## Configuration

Copy the safe template and replace every `CHANGE_ME` value:

```powershell
Copy-Item .env.example .env
```

At minimum, Docker Compose requires independent values for:

- `MYSQL_ROOT_PASSWORD`
- `APP_JWT_SECRET` (at least 32 characters)
- `APP_QR_SECRET` (a different value, at least 32 characters)

Payment, SMTP, and Google OAuth settings are optional and disabled or empty by default. Do not commit `.env`, real gateway credentials, OAuth client secrets, or mail passwords.

## Running with Docker

```powershell
docker compose up --build
```

Default local endpoints:

| Service | URL |
| --- | --- |
| Web client | `http://localhost:3000` |
| Backend API | `http://localhost:8080/api` |
| Mailpit inbox | `http://localhost:8025` |
| phpMyAdmin | `http://localhost:8081` |

The password-reset token is not exposed by default. Set `APP_PASSWORD_RESET_EXPOSE_TOKEN=true` only for an isolated local demonstration.

## Running Components Separately

Backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Provide `DB_PASSWORD`, `APP_JWT_SECRET`, and `APP_QR_SECRET` through environment variables before starting the backend.

Flutter client on an Android emulator:

```powershell
cd mobile\smartparking_mobile
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api
```

Use `http://localhost:8080/api` for Flutter web/Windows or your computer's LAN address for a physical device.

## Verification

```powershell
cd backend
.\mvnw.cmd test

cd ..\mobile\smartparking_mobile
flutter analyze
```

## Gate Workflow

1. A user reserves an available slot and receives a signed QR ticket.
2. Gate staff open `http://localhost:3000/scanner.html` with an authorized account.
3. Check-in changes the booking to `CHECKED_IN` and the slot to `OCCUPIED`.
4. Check-out completes the booking, releases the slot, and applies the configured charge rules.

## Known Limitations

- OCR is a deterministic simulation, not image recognition.
- Gateway integrations require separate sandbox accounts and public callback URLs.
- Google sign-in, SMTP, and HTTPS need external configuration not included in the repository.
- The web client is a static JavaScript application rather than a bundled frontend framework.
- The current Flutter client has static analysis but no automated test suite yet.
- The project has not undergone a production security or load assessment.

## Project Status

Completed academic project maintained for portfolio demonstrations and further learning.

## Author

Nguyễn Chí Thanh
