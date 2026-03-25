const CACHE_NAME = 'smartparking-cache-v1';

const CORE_ASSETS = [
    '/',
    '/index.html',
    '/login.html',
    '/register.html',
    '/dashboard.html',
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
                    return cached;
                }

                return fetch(request)
                    .then((res) => {
                        if (res && res.ok && request.url.startsWith(self.location.origin)) {
                            const copy = res.clone();
                            caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
                        }
                        return res;
                    })
                    .catch(() => new Response('Offline', { status: 503, headers: { 'Content-Type': 'text/plain' } }));
            })
    );
});
