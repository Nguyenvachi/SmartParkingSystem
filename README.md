# SmartParkingSystem
J2EE &amp; Tester

## Run with Docker (recommended)

Prerequisites: Docker Desktop.

```bash
docker compose up --build
```

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
