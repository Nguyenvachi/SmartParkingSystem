/**
 * Configuration File - Shared constants
 * Tất cả file JavaScript import config từ đây để tránh duplicate
 */

// NOTE (rule: "Chỉ Thêm - Không Bớt"):
// Old defaults kept for traceability.
// const API_BASE_URL = 'http://localhost:8080/api';
// const WS_BASE_URL = 'http://localhost:8080/ws';

// Allow overriding at runtime by defining `window.SMARTPARKING_CONFIG` BEFORE loading this file.
// Example:
// <script>
//   window.SMARTPARKING_CONFIG = {
//     API_BASE_URL: 'https://your-domain/api',
//     WS_BASE_URL: 'https://your-domain/ws',
//     FRONTEND_LOGIN_URL: 'https://your-domain/login.html',
//     FRONTEND_DASHBOARD_URL: 'https://your-domain/dashboard.html',
//     OAUTH2_GOOGLE_AUTH_URL: 'https://your-domain/oauth2/authorization/google'
//   };
// </script>
const SMARTPARKING_CONFIG = (window && window.SMARTPARKING_CONFIG) ? window.SMARTPARKING_CONFIG : {};

// Default to same-origin so the app works when accessed via LAN IP on mobile.
// Nginx proxies /api and /ws to backend inside Docker.
const API_BASE_URL = SMARTPARKING_CONFIG.API_BASE_URL || `${window.location.origin}/api`;
const WS_BASE_URL = SMARTPARKING_CONFIG.WS_BASE_URL || `${window.location.origin}/ws`;

// Old clean-URL routes kept for traceability.
// const FRONTEND_BASE_URL = window.location.origin;
// const FRONTEND_LOGIN_URL = `${FRONTEND_BASE_URL}/login`;
// const FRONTEND_DASHBOARD_URL = `${FRONTEND_BASE_URL}/dashboard`;

// Default to clean URLs to avoid token/query loss on html->route rewrites.
const FRONTEND_BASE_URL = window.location.origin;
const FRONTEND_LOGIN_URL = SMARTPARKING_CONFIG.FRONTEND_LOGIN_URL || `${FRONTEND_BASE_URL}/login`;
const FRONTEND_DASHBOARD_URL = SMARTPARKING_CONFIG.FRONTEND_DASHBOARD_URL || `${FRONTEND_BASE_URL}/dashboard`;

// OAuth2 entrypoints on backend
// const OAUTH2_GOOGLE_AUTH_URL = 'http://localhost:8080/oauth2/authorization/google';
const OAUTH2_GOOGLE_AUTH_URL = SMARTPARKING_CONFIG.OAUTH2_GOOGLE_AUTH_URL || 'http://localhost:8080/oauth2/authorization/google';

// ===== PWA (optional / for mobile scoring) =====
try {
	if ('serviceWorker' in navigator) {
		window.addEventListener('load', () => {
			navigator.serviceWorker.register('service-worker.js').catch(() => {
				// ignore registration errors in dev (e.g., when served from file://)
			});
		});
	}
} catch (e) {
	// ignore
}
