const CACHE_NAME = 'smartparking-cache-v4';

const CORE_ASSETS = [
    '/',
    '/index.html',
    '/login.html',
    '/register.html',
    '/dashboard.html',
    '/scanner.html',
    '/manifest.json',
    '/service-worker.js',
    '/css/style.css',
    '/js/config.js',
    '/js/auth.js',
    '/js/api.js',
    '/js/main.js',
    '/js/admin.js'
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then((cache) => cache.addAll(CORE_ASSETS))
            .then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(keys.map((key) => key !== CACHE_NAME ? caches.delete(key) : null)))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', (event) => {
    const { request } = event;

    // Navigation / document requests are special: returning a cached redirect/opaqueredirect
    // can cause Chrome to fail the navigation with ERR_FAILED.
    if (request.mode === 'navigate' || request.destination === 'document') {
        const url = new URL(request.url);
        const rewriteMap = {
            '/login': '/login.html',
            '/register': '/register.html',
            '/dashboard': '/dashboard.html',
            '/scanner': '/scanner.html'
        };
        const rewrittenPath = rewriteMap[url.pathname];

        event.respondWith(
            fetch(request)
                .then(async (res) => {
                    if (res && res.ok) {
                        return res;
                    }

                    // Support clean URLs when running static hosting (e.g., npx serve).
                    if (res && res.status === 404 && rewrittenPath) {
                        try {
                            const rewritten = await fetch(rewrittenPath);
                            if (rewritten && rewritten.ok) return rewritten;
                        } catch (_) {
                            // ignore
                        }
                        const cachedRewritten = await caches.match(rewrittenPath);
                        if (cachedRewritten) return cachedRewritten;
                    }

                    return res;
                })
                .catch(async () => {
                    // Best-effort fallback for offline. Prefer rewritten, then requested doc, then index.
                    if (rewrittenPath) {
                        const cachedRewritten = await caches.match(rewrittenPath);
                        if (cachedRewritten) return cachedRewritten;
                    }

                    const cachedDoc = await caches.match(request);
                    if (cachedDoc && cachedDoc.status >= 200 && cachedDoc.status < 300) {
                        return cachedDoc;
                    }

                    const cachedIndex = await caches.match('/index.html');
                    if (cachedIndex) {
                        return cachedIndex;
                    }

                    return new Response('Offline', {
                        status: 503,
                        headers: { 'Content-Type': 'text/plain' }
                    });
                })
        );
        return;
    }

    // Do not cache API calls
    if (request.url.includes('/api/')) {
        return;
    }

    // Only handle GET requests
    if (request.method !== 'GET') {
        return;
    }

    event.respondWith(
        caches.match(request)
            .then((cached) => {
                if (cached) {
                    // Do not serve cached redirects for subresources.
                    if (cached.type === 'opaqueredirect' || (cached.status >= 300 && cached.status < 400)) {
                        cached = null;
                    }
                }

                if (cached) {
                    return cached;
                }

                return fetch(request)
                    .then((res) => {
                        // Only cache same-origin successful non-redirect responses.
                        if (res && res.ok
                            && res.type !== 'opaqueredirect'
                            && !(res.status >= 300 && res.status < 400)
                            && request.url.startsWith(self.location.origin)) {
                            const copy = res.clone();
                            caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
                        }
                        return res;
                    })
                    .catch(() => new Response('Offline', { status: 503, headers: { 'Content-Type': 'text/plain' } }));
            })
    );
});
