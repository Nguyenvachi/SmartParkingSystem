/**
 * Configuration File - Shared constants
 * Tất cả file JavaScript import config từ đây để tránh duplicate
 */

const API_BASE_URL = 'http://localhost:8080/api';
const WS_BASE_URL = 'http://localhost:8080/ws';

// Canonical routes on frontend dev server (npx serve uses clean URLs)
const FRONTEND_BASE_URL = window.location.origin;
const FRONTEND_LOGIN_URL = `${FRONTEND_BASE_URL}/login`;
const FRONTEND_DASHBOARD_URL = `${FRONTEND_BASE_URL}/dashboard`;

// OAuth2 entrypoints on backend
const OAUTH2_GOOGLE_AUTH_URL = 'http://localhost:8080/oauth2/authorization/google';
