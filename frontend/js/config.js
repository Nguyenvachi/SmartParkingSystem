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

// ===== Console silencing (production hardening) =====
// NOTE: This does not make the app truly "secure" (DevTools can still inspect network/source),
// but it prevents accidental leakage of operational info via console logs.
// To temporarily enable logs for debugging:
// - Add `?debug=1` to the URL, OR
// - Set `localStorage.sp_debug = '1'`, OR
// - Set `window.SMARTPARKING_CONFIG = { DEBUG: true }` before loading config.js
let SMARTPARKING_DEBUG = false;
try {
	const q = new URLSearchParams(window.location.search || '');
	SMARTPARKING_DEBUG = !!SMARTPARKING_CONFIG.DEBUG
		|| q.get('debug') === '1'
		|| (typeof localStorage !== 'undefined' && localStorage.getItem('sp_debug') === '1');
} catch (_) {
	SMARTPARKING_DEBUG = !!SMARTPARKING_CONFIG.DEBUG;
}

// Default to same-origin so the app works when accessed via LAN IP on mobile.
// In Docker/VPS, Nginx proxies /api and /ws to backend.
// In local dev (npx serve), there is no proxy, so we fall back to backend :8080.
const SAME_ORIGIN_API = `${window.location.origin}/api`;
const SAME_ORIGIN_WS = `${window.location.origin}/ws`;
const SAME_ORIGIN_OAUTH2 = `${window.location.origin}/oauth2/authorization/google`;

const isLocalhost = (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1');
const isLikelyServeDev = isLocalhost && String(window.location.port) === '3000';

// Always allow logs on localhost unless explicitly disabled.
SMARTPARKING_DEBUG = SMARTPARKING_DEBUG || isLocalhost;

if (!SMARTPARKING_DEBUG) {
	try {
		const noop = function () { };
		const methods = [
			'log', 'debug', 'info', 'warn', 'error',
			'trace', 'group', 'groupCollapsed', 'groupEnd',
			'time', 'timeEnd'
		];
		if (typeof console !== 'undefined') {
			methods.forEach(m => {
				try {
					console[m] = noop;
				} catch (_) {
					// ignore
				}
			});
		}

		// Suppress default console reporting for runtime errors.
		// (DevTools may still show some errors depending on browser settings.)
		window.addEventListener('error', (e) => {
			try { e.preventDefault(); } catch (_) { }
		}, true);
		window.addEventListener('unhandledrejection', (e) => {
			try { e.preventDefault(); } catch (_) { }
		}, true);
	} catch (_) {
		// ignore
	}
}

const API_BASE_URL = SMARTPARKING_CONFIG.API_BASE_URL
	|| (isLikelyServeDev ? 'http://localhost:8080/api' : SAME_ORIGIN_API);
const WS_BASE_URL = SMARTPARKING_CONFIG.WS_BASE_URL
	|| (isLikelyServeDev ? 'http://localhost:8080/ws' : SAME_ORIGIN_WS);

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
const OAUTH2_GOOGLE_AUTH_URL = SMARTPARKING_CONFIG.OAUTH2_GOOGLE_AUTH_URL
	|| (isLikelyServeDev ? 'http://localhost:8080/oauth2/authorization/google' : SAME_ORIGIN_OAUTH2);

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
