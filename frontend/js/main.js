/**
 * Frontend JavaScript cho Smart Parking Dashboard
 * Tech Key: Tính năng #5 - Real-time Map Update (WebSocket)
 * 
 * Chức năng:
 * 1. Kết nối WebSocket đến Backend (STOMP over SockJS)
 * 2. Subscribe topic /topic/parking-updates
 * 3. Render lưới bãi xe (5x4 grid)
 * 4. Cập nhật màu slot real-time khi nhận message
 * 5. Gọi API REST để lấy dữ liệu ban đầu
 */

// ============================================
// 1. CẤU HÌNH & BIẾN GLOBAL
// ============================================

let stompClient = null; // STOMP client instance
let slotsData = {}; // Lưu trữ dữ liệu slots (key: slotName, value: slotObject)
let unauthorizedRedirectScheduled = false;

let dashboardIsAdmin = false;

function isAdminRole(role) {
    return role === 'ROLE_ADMIN' || role === 'ROLE_BRANCH_ADMIN';
}

function resolveDashboardRole() {
    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
    return user?.role || 'ROLE_USER';
}

function applyRoleBasedDashboardUI() {
    const role = resolveDashboardRole();
    dashboardIsAdmin = isAdminRole(role);

    // Hide user-only modules for admin roles
    document.querySelectorAll('.user-only').forEach(el => {
        if (dashboardIsAdmin) {
            el.classList.add('d-none');
        } else {
            el.classList.remove('d-none');
        }
    });

    // Prevent switching to hidden tabs (navbar links call switchSidebarTab)
    if (dashboardIsAdmin) {
        try {
            const guideTrigger = document.querySelector('[data-bs-target="#tabGuide"]');
            if (guideTrigger) {
                bootstrap.Tab.getOrCreateInstance(guideTrigger).show();
            }
        } catch (_) {
            // no-op
        }
    }
}

// Phase 4: Selected top-up provider for the main "Nạp tiền" button
let selectedTopUpProvider = null;
let gatewayTopUpInFlight = false;

function setGatewayTopUpLoading(isLoading) {
    const btnMomo = document.getElementById('btnTopUpMomo');
    const btnVnpay = document.getElementById('btnTopUpVnpay');
    const btnSubmit = document.querySelector('#topUpForm button[type="submit"]');

    [btnMomo, btnVnpay, btnSubmit].forEach(btn => {
        if (!btn) return;
        btn.disabled = isLoading;
    });

    if (btnMomo) {
        btnMomo.textContent = isLoading ? 'Đang xử lý...' : 'MoMo';
    }
    if (btnVnpay) {
        btnVnpay.textContent = isLoading ? 'Đang xử lý...' : 'VNPay';
    }
}

function selectTopUpProvider(provider) {
    if (provider !== 'momo' && provider !== 'vnpay') {
        selectedTopUpProvider = null;
        return;
    }
    selectedTopUpProvider = provider;
}

function bootstrapOAuthUserFromCurrentUrl() {
    try {
        const params = new URLSearchParams(window.location.search || '');
        const userId = params.get('userId');
        const email = params.get('email');
        const token = params.get('token');

        if (!userId || !email || !token) {
            return false;
        }

        const role = params.get('role') || 'ROLE_USER';
        const fullName = params.get('fullName') || '';
        const branchCode = params.get('branchCode') || '';
        const avatarUrl = params.get('avatarUrl') || '';

        const userData = {
            userId: Number(userId),
            email,
            fullName,
            role,
            branchCode,
            avatarUrl,
            token,
            loginMethod: 'GOOGLE'
        };

        localStorage.setItem('user', JSON.stringify(userData));
        // Xóa query nhạy cảm sau khi đã lưu session
        window.history.replaceState({}, document.title, window.location.pathname);
        return true;
    } catch (e) {
        return false;
    }
}

// ============================================
// 2. KHỞI TẠO KHI TRANG LOAD
// ============================================

document.addEventListener('DOMContentLoaded', function () {
    // First priority: hydrate directly from current URL query (independent from other scripts).
    bootstrapOAuthUserFromCurrentUrl();

    // Google OAuth2 redirect returns token in query params.
    // Hydrate localStorage BEFORE we enforce token presence.
    if (typeof hydrateUserFromOAuth2QueryParams === 'function') {
        try { hydrateUserFromOAuth2QueryParams(); } catch (_) { /* ignore */ }
    }

    // Kiểm tra token hết hạn ngay khi load trang
    const redirected = checkTokenAndRedirect();
    if (redirected) return; // Đang chuyển hướng, dừng lại

    applyRoleBasedDashboardUI();

    // Handle payment return params (MoMo/VNPay)
    handlePaymentReturnParams();

    // Bước 1: Lấy dữ liệu slots ban đầu từ API
    fetchAllSlots();

    // Bước 2: Kết nối WebSocket
    connectWebSocket();

    // Phase 4 (user-only modules)
    if (!dashboardIsAdmin) {
        loadRecommendation();
        loadWalletSummary();
        loadAvailableVouchers();

        document.getElementById('topUpForm')?.addEventListener('submit', async function (event) {
            event.preventDefault();
            await topUpWallet();
        });

        document.getElementById('btnTopUpMomo')?.addEventListener('click', async function () {
            selectTopUpProvider('momo');
            await startGatewayTopUp('momo');
        });

        document.getElementById('btnTopUpVnpay')?.addEventListener('click', async function () {
            selectTopUpProvider('vnpay');
            await startGatewayTopUp('vnpay');
        });

        document.getElementById('withdrawForm')?.addEventListener('submit', async function (event) {
            event.preventDefault();
            await withdrawWallet();
        });

        document.getElementById('ocrForm')?.addEventListener('submit', async function (event) {
            event.preventDefault();
            await simulateOcr();
        });
    }
});

// ============================================
// 2.2 PAYMENT RETURN HANDLER (MoMo/VNPay)
// ============================================
function handlePaymentReturnParams() {
    const params = new URLSearchParams(window.location.search);
    const payment = params.get('payment');
    if (!payment) return;

    const provider = (params.get('provider') || '').toUpperCase();
    const orderId = params.get('orderId') || '';
    const message = params.get('message') || '';

    if (payment === 'success') {
        showToast('success', `Thanh toán ${provider} thành công${orderId ? ` (order ${orderId})` : ''}.`);
        loadWalletSummary();
    } else {
        showToast('warning', `Thanh toán ${provider || ''} thất bại. ${message}`.trim());
    }

    // Clean URL (remove query params)
    window.history.replaceState({}, document.title, window.location.pathname);
}

// ============================================
// 2.3 START TOPUP VIA PAYMENT GATEWAY
// ============================================
async function startGatewayTopUp(provider) {
    if (gatewayTopUpInFlight) {
        showToast('warning', 'Yêu cầu nạp tiền đang được xử lý, vui lòng đợi...');
        return;
    }

    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) return;

    const amountInput = document.getElementById('topUpAmount');
    const descriptionInput = document.getElementById('topUpDescription');
    const amount = Number(amountInput?.value || 0);

    if (!amount || amount <= 0) {
        showToast('warning', 'Vui lòng nhập số tiền nạp hợp lệ.');
        return;
    }
    if (!Number.isInteger(amount)) {
        showToast('warning', 'Số tiền VND phải là số nguyên.');
        return;
    }
    // Keep in sync with backend default: app.wallet.minimum-topup=10000
    if (amount < 10000) {
        showToast('warning', 'Số tiền nạp tối thiểu là 10.000 VND.');
        return;
    }

    const path = provider === 'momo' ? '/payments/topup/momo'
        : provider === 'vnpay' ? '/payments/topup/vnpay'
            : null;
    if (!path) {
        showToast('danger', 'Provider không hợp lệ.');
        return;
    }

    try {
        gatewayTopUpInFlight = true;
        setGatewayTopUpLoading(true);

        const res = await fetch(`${API_BASE_URL}${path}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${user.token}`
            },
            body: JSON.stringify({ amount, description: descriptionInput?.value || '' })
        });

        const data = await res.json();
        if (res.status === 401 || res.status === 403) { handleUnauthorized(res.status); return; }
        if (!res.ok) throw new Error(data.message || 'Không thể tạo yêu cầu thanh toán');
        if (!data.paymentUrl) throw new Error('Gateway không trả về paymentUrl');

        showToast('info', 'Đang chuyển sang cổng thanh toán...');
        window.location.href = data.paymentUrl;
    } catch (err) {
        showToast('danger', err.message || String(err));
    } finally {
        // If browser navigates away, this may not run; it is still useful when API call fails.
        gatewayTopUpInFlight = false;
        setGatewayTopUpLoading(false);
    }
}

// ============================================
// 2.1 KIỂM TRA TOKEN HẾT HẠN (Client-side)
// Decode JWT payload (không cần verify signature)
// ============================================
function checkTokenAndRedirect() {
    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) {
        const loginUrl = (typeof FRONTEND_LOGIN_URL !== 'undefined' && FRONTEND_LOGIN_URL)
            ? FRONTEND_LOGIN_URL : '/login';
        try { sessionStorage.setItem('sp_auth_redirect_reason', 'missing_token_on_dashboard'); } catch (e) { /* ignore */ }
        localStorage.removeItem('user');
        window.location.href = loginUrl + '?reason=expired';
        return true;
    }

    const tokenValid = (typeof hasValidToken === 'function') ? hasValidToken(user) : true;
    if (!tokenValid) {
        localStorage.removeItem('user');
        const loginUrl = (typeof FRONTEND_LOGIN_URL !== 'undefined' && FRONTEND_LOGIN_URL)
            ? FRONTEND_LOGIN_URL : '/login';
        try { sessionStorage.setItem('sp_auth_redirect_reason', 'invalid_token_on_dashboard'); } catch (e) { /* ignore */ }
        window.location.href = loginUrl + '?reason=expired';
        return true;
    }
    return false;
}

// ============================================
// 3. FETCH DỮ LIỆU SLOTS TỪ API (REST)
// ============================================

async function fetchAllSlots() {
    try {
        const response = await fetch(`${API_BASE_URL}/slots`);

        if (!response.ok) {
            throw new Error(`HTTP error! Status: ${response.status}`);
        }

        const slots = await response.json();

        slotsData = {};

        // Lưu vào object để tra cứu nhanh
        slots.forEach(slot => {
            slotsData[slot.slotName] = slot;
        });

        // Render lưới
        renderParkingGrid();

    } catch (error) {
        showError('Không thể tải dữ liệu bãi xe. Vui lòng kiểm tra kết nối.');
    }
}

// ============================================
// 4. RENDER LƯỚI BÃI XE (5 HÀNG X 4 CỘT)
// ============================================

function renderParkingGrid() {
    const gridContainer = document.getElementById('parking-grid');
    gridContainer.innerHTML = ''; // Xóa loading spinner

    const rows = ['A', 'B', 'C', 'D', 'E']; // 5 hàng
    const cols = 4; // 4 cột

    rows.forEach(row => {
        // Tạo 1 hàng (div chứa 4 slots)
        const rowDiv = document.createElement('div');
        rowDiv.className = 'd-flex gap-2 justify-content-center';

        for (let col = 1; col <= cols; col++) {
            const slotName = `${row}${String(col).padStart(2, '0')}`; // A01, A02, B01...
            const slotDiv = createSlotElement(slotName);
            rowDiv.appendChild(slotDiv);
        }

        gridContainer.appendChild(rowDiv);
    });

}

// ============================================
// 5. TẠO ELEMENT CHO 1 SLOT
// ============================================

function createSlotElement(slotName) {
    const slotData = slotsData[slotName] || createPlaceholderSlot(slotName);

    const slotDiv = document.createElement('div');
    slotDiv.className = 'parking-slot';
    slotDiv.id = `slot-${slotName}`;
    slotDiv.setAttribute('data-slot-name', slotName);

    // Thêm class màu dựa trên status
    slotDiv.classList.add(getStatusClass(slotData.status));

    // Nội dung slot
    slotDiv.innerHTML = `
        <div class="slot-name">${slotName}</div>
        <div class="slot-status">${getStatusText(slotData.status)}</div>
    `;

    // Event click (để đặt chỗ)
    slotDiv.addEventListener('click', () => {
        const latestSlotData = slotsData[slotName] || createPlaceholderSlot(slotName);
        handleSlotClick(slotName, latestSlotData);
    });

    return slotDiv;
}

// ============================================
// 6. HELPER FUNCTIONS - MAPPING STATUS
// ============================================

function getStatusClass(status) {
    const statusMap = {
        'AVAILABLE': 'slot-available',
        'RESERVED': 'slot-reserved',
        'OCCUPIED': 'slot-occupied',
        'MAINTENANCE': 'slot-maintenance',
        'UNCONFIGURED': 'slot-maintenance'
    };
    return statusMap[status] || 'slot-maintenance';
}

function getStatusText(status) {
    const textMap = {
        'AVAILABLE': 'Trống',
        'RESERVED': 'Đã đặt',
        'OCCUPIED': 'Đang đỗ',
        'MAINTENANCE': 'Bảo trì',
        'UNCONFIGURED': 'Chưa tạo'
    };
    return textMap[status] || 'N/A';
}

function createPlaceholderSlot(slotName) {
    return {
        id: null,
        slotName,
        type: 'N/A',
        status: 'UNCONFIGURED',
        pricePerHour: null
    };
}

// ============================================
// 7. XỬ LÝ CLICK SLOT (ĐẶT CHỖ)
// ============================================

function handleSlotClick(slotName, slotData) {
    if (!slotData || !slotData.id) {
        showToast('warning', `Slot ${slotName} chưa được tạo trong hệ thống.`);
        return;
    }

    // Admin roles: view slot details (no booking actions)
    if (dashboardIsAdmin) {
        showBookingModal(slotName, slotData);
        return;
    }

    if (slotData.status !== 'AVAILABLE') {
        showToast('warning', `Slot ${slotName} không khả dụng (${getStatusText(slotData.status)})`);
        return;
    }

    // Phase 3: Hiện modal đặt chỗ thật
    showBookingModal(slotName, slotData);
}

// ============================================
// PHASE 3 — BOOKING LOGIC
// ============================================

let selectedSlotData = null;

/**
 * Hiện modal xác nhận đặt chỗ
 */
function showBookingModal(slotName, slotData) {
    if (!slotData || !slotData.id) {
        showToast('warning', `Slot ${slotName} chưa được tạo trong hệ thống.`);
        return;
    }

    // Only users can create bookings
    selectedSlotData = (!dashboardIsAdmin && slotData.status === 'AVAILABLE') ? slotData : null;
    document.getElementById('modalSlotName').textContent = slotName;
    document.getElementById('modalSlotType').textContent = slotData.type || 'N/A';
    document.getElementById('modalSlotStatus').textContent = getStatusText(slotData.status);
    document.getElementById('modalSlotPrice').textContent =
        slotData.pricePerHour
            ? slotData.pricePerHour.toLocaleString('vi-VN') + ' VNĐ/giờ'
            : 'Chưa cấu hình';

    const occupiedPanel = document.getElementById('occupiedDetailsPanel');
    if (occupiedPanel) {
        const isOccupied = (slotData.status || '').toUpperCase() === 'OCCUPIED';
        occupiedPanel.classList.toggle('d-none', !isOccupied);
        if (isOccupied) {
            document.getElementById('modalActiveBookingId').textContent = slotData.activeBookingId ?? '—';
            document.getElementById('modalActiveVehiclePlate').textContent = slotData.activeVehiclePlate || '—';
            document.getElementById('modalActiveCheckInTime').textContent = slotData.activeCheckInTime
                ? new Date(slotData.activeCheckInTime).toLocaleString('vi-VN')
                : '—';
        }
    }

    const modalTitle = document.getElementById('bookingModalLabel');
    if (modalTitle) {
        modalTitle.innerHTML = dashboardIsAdmin
            ? '<i class="bi bi-info-circle me-2"></i>Thông tin Slot'
            : '<i class="bi bi-p-circle me-2"></i>Xác nhận Đặt chỗ';
    }

    const confirmBtn = document.getElementById('btnConfirmBooking');
    if (confirmBtn) {
        const canBook = !dashboardIsAdmin && (slotData.status === 'AVAILABLE');
        confirmBtn.classList.toggle('d-none', !canBook);
    }

    const modal = new bootstrap.Modal(document.getElementById('bookingModal'));
    modal.show();
}

/**
 * Xác nhận đặt chỗ — gọi API POST /api/bookings
 * Tech Key #1: Nếu bị race condition, backend trả về lỗi "Slot vừa được đặt mất"
 */
async function confirmBooking() {
    if (!selectedSlotData) return;

    if (!selectedSlotData.id) {
        showToast('warning', 'Slot này chưa được tạo trong hệ thống, chưa thể đặt chỗ.');
        return;
    }

    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) {
        alert('Bạn cần đăng nhập để đặt chỗ!');
        window.location.href = FRONTEND_LOGIN_URL;
        return;
    }

    if (typeof hasValidToken === 'function' && !hasValidToken(user)) {
        handleUnauthorized();
        return;
    }

    const btn = document.getElementById('btnConfirmBooking');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang đặt...';

    try {
        // Old (kept): const res = await fetch(`http://localhost:8080/api/bookings`, {
        const res = await fetch(`${API_BASE_URL}/bookings`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${user.token}`
            },
            body: JSON.stringify({ slotId: selectedSlotData.id })
        });

        const data = await res.json();

        if (res.status === 401 || res.status === 403) {
            handleUnauthorized(res.status);
            return;
        }
        if (!res.ok) {
            // Tech Key #1: Optimistic Locking conflict message từ backend
            throw new Error(data.message || 'Đặt chỗ thất bại');
        }

        // Đóng modal đặt chỗ
        bootstrap.Modal.getInstance(document.getElementById('bookingModal')).hide();

        // Hiện modal QR Code (Tech Key #7)
        showQRModal(data);

        // Reload history
        loadBookingHistory();

    } catch (err) {
        showToast('danger', err.message);
    } finally {
        btn.disabled = false;
        btn.innerHTML = 'Xác nhận Đặt chỗ';
    }
}

/**
 * Hiện modal QR Code vé điện tử
 * Tech Key #7: Digital Signature — backend đã ký, FE chỉ hiển thị
 */
function showQRModal(bookingData) {
    document.getElementById('qrBookingId').textContent = bookingData.bookingId;
    document.getElementById('qrSlotName').textContent = bookingData.slotName;
    document.getElementById('qrExpiry').textContent = bookingData.expiryTime
        ? new Date(bookingData.expiryTime).toLocaleTimeString('vi-VN')
        : '—';

    // Render ảnh QR từ Base64
    const imgEl = document.getElementById('qrCodeImage');
    if (bookingData.qrCodeBase64 && bookingData.qrCodeBase64.length > 0) {
        imgEl.src = `data:image/png;base64,${bookingData.qrCodeBase64}`;
        imgEl.style.display = 'block';
        document.getElementById('qrPlaceholder').style.display = 'none';
    } else {
        imgEl.style.display = 'none';
        document.getElementById('qrPlaceholder').style.display = 'block';
    }

    const qrModal = new bootstrap.Modal(document.getElementById('qrModal'));
    qrModal.show();
}

/**
 * Load chi tiết booking — GET /api/bookings/{id}
 * Add-on: dùng để mở lại QR/chi tiết từ tab lịch sử.
 */
async function openBookingDetails(bookingId) {
    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) return;

    try {
        const res = await fetch(`${API_BASE_URL}/bookings/${bookingId}`, {
            headers: { 'Authorization': `Bearer ${user.token}` }
        });
        const data = await res.json();
        if (res.status === 401 || res.status === 403) { handleUnauthorized(res.status); return; }
        if (!res.ok) throw new Error(data.message || 'Không tải được chi tiết booking');

        showQRModal(data);
    } catch (err) {
        showToast('danger', err.message);
    }
}

/**
 * Hủy booking — DELETE /api/bookings/{id}
 */
async function cancelBooking(bookingId) {
    if (!confirm('Bạn có chắc muốn hủy booking này?')) return;
    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) return;

    try {
        // Old (kept): const res = await fetch(`http://localhost:8080/api/bookings/${bookingId}`, {
        const res = await fetch(`${API_BASE_URL}/bookings/${bookingId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${user.token}` }
        });
        const data = await res.json();
        if (res.status === 401 || res.status === 403) { handleUnauthorized(res.status); return; }
        if (!res.ok) throw new Error(data.message || 'Hủy thất bại');

        showToast('warning', `Đã hủy booking #${bookingId}. Slot được trả về trạng thái trống.`);
        loadBookingHistory();
        loadWalletSummary();

    } catch (err) {
        showToast('danger', err.message);
    }
}

/**
 * Load lịch sử booking của user — GET /api/bookings
 */
async function loadBookingHistory() {
    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) return;

    const container = document.getElementById('bookingHistoryList');
    container.innerHTML = '<p class="text-muted text-center small py-2">Đang tải...</p>';

    try {
        // Old (kept): const res = await fetch('http://localhost:8080/api/bookings', {
        const res = await fetch(`${API_BASE_URL}/bookings`, {
            headers: { 'Authorization': `Bearer ${user.token}` }
        });
        const bookings = await res.json();
        if (res.status === 401 || res.status === 403) {
            handleUnauthorized(res.status);
            return;
        }
        if (!res.ok) throw new Error('Không tải được lịch sử');

        if (!bookings || bookings.length === 0) {
            container.innerHTML = '<p class="text-muted text-center small py-2">Chưa có booking nào.</p>';
            return;
        }

        container.innerHTML = bookings.map(b => `
            <div class="booking-card mb-2 p-2 rounded ${getBookingCardClass(b.status)}">
                <div class="d-flex justify-content-between align-items-start">
                    <div>
                        <strong>#${b.bookingId}</strong>
                        <span class="ms-2 badge ${getStatusBadge(b.status)}">${b.status}</span><br>
                        <small>Slot: <strong>${b.slotName}</strong></small><br>
                        <small>${new Date(b.bookingTime).toLocaleString('vi-VN')}</small>
                        ${b.status === 'PENDING' ? `<br><small class="text-danger">Hết hạn: ${new Date(b.expiryTime).toLocaleTimeString('vi-VN')}</small>` : ''}
                        ${b.status === 'CHECKED_IN' ? `<br><small class="text-primary">Đã vào bãi: ${new Date(b.checkInTime).toLocaleString('vi-VN')}</small>` : ''}
                        ${b.status === 'COMPLETED' ? `<br><small class="text-success">Tổng phí: ${formatCurrency(b.totalAmount || 0)}</small>` : ''}
                    </div>
                    <div class="d-flex flex-column gap-1">
                        <button class="btn btn-xs btn-outline-primary py-0 px-1" onclick="openBookingDetails(${b.bookingId})" title="Xem QR / Chi tiết">
                            <i class="bi bi-qr-code"></i>
                        </button>
                        ${b.status === 'PENDING' ? `
                            <button class="btn btn-xs btn-outline-danger py-0 px-1" onclick="cancelBooking(${b.bookingId})" title="Hủy">
                                <i class="bi bi-x-lg"></i>
                            </button>
                        ` : ''}
                    </div>
                </div>
            </div>
        `).join('');

    } catch (err) {
        container.innerHTML = `<p class="text-danger small text-center">${err.message}</p>`;
    }
}

/**
 * Load toàn bộ giao dịch ví — GET /api/wallet/transactions
 * Add-on: FE trước đây chỉ render recentTransactions từ GET /api/wallet.
 */
async function loadWalletTransactionsFull() {
    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) return;

    const container = document.getElementById('walletTransactionsList');
    if (container) {
        container.innerHTML = '<div class="text-muted small">Đang tải tất cả giao dịch...</div>';
    }

    try {
        const res = await fetch(`${API_BASE_URL}/wallet/transactions`, {
            headers: { 'Authorization': `Bearer ${user.token}` }
        });
        const data = await res.json();
        if (res.status === 401 || res.status === 403) { handleUnauthorized(res.status); return; }
        if (!res.ok) throw new Error(data.message || 'Không tải được danh sách giao dịch');

        renderWalletTransactions(Array.isArray(data) ? data : []);
        showToast('success', `Đã tải ${Array.isArray(data) ? data.length : 0} giao dịch.`);
    } catch (err) {
        if (container) {
            container.innerHTML = `<div class="text-danger small">${err.message}</div>`;
        }
    }
}

function getBookingCardClass(status) {
    return {
        PENDING: 'bg-warning bg-opacity-10', CHECKED_IN: 'bg-primary bg-opacity-10',
        COMPLETED: 'bg-success bg-opacity-10', CANCELLED: 'bg-secondary bg-opacity-10'
    }[status] || '';
}

function getStatusBadge(status) {
    return {
        PENDING: 'bg-warning text-dark', CHECKED_IN: 'bg-primary',
        COMPLETED: 'bg-success', CANCELLED: 'bg-secondary'
    }[status] || 'bg-secondary';
}

function formatCurrency(value) {
    return Number(value || 0).toLocaleString('vi-VN') + ' VND';
}

function switchSidebarTab(tabId) {
    const trigger = document.querySelector(`[data-bs-target="#${tabId}"]`);
    if (!trigger) return;
    bootstrap.Tab.getOrCreateInstance(trigger).show();

    if (tabId === 'tabHistory') {
        loadBookingHistory();
    }
    if (tabId === 'tabWallet') {
        loadWalletSummary();
    }
    if (tabId === 'tabProfile') {
        if (typeof loadProfileTab === 'function') {
            loadProfileTab();
        }
    }
}

async function loadRecommendation(vehicleType = null) {
    const panel = document.getElementById('recommendationPanel');
    if (!panel) return;

    panel.innerHTML = '<div class="text-muted">Đang tính toán gợi ý...</div>';

    try {
        const query = vehicleType ? `?vehicleType=${encodeURIComponent(vehicleType)}` : '';
        const res = await fetch(`${API_BASE_URL}/slots/recommendation${query}`);
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Không lấy được gợi ý chỗ đỗ');

        const recommended = data.recommendedSlot;
        const alternatives = data.alternativeSlots || [];

        panel.innerHTML = `
            <div class="border rounded p-2 bg-light-subtle">
                <div class="fw-semibold text-primary mb-1">Đề xuất chính: ${recommended.slotName}</div>
                <div class="small mb-2">Loại xe: ${recommended.type} • Giá: ${formatCurrency(recommended.pricePerHour)}</div>
                <div class="small text-muted mb-2">${data.explanation || ''}</div>
                <button class="btn btn-primary btn-sm w-100 mb-2" onclick="bookRecommendedSlot('${recommended.slotName}')">
                    Đặt slot ${recommended.slotName}
                </button>
                <div class="small text-muted">Phương án khác: ${alternatives.length > 0 ? alternatives.map(slot => slot.slotName).join(', ') : 'Không có'}</div>
            </div>
        `;
    } catch (err) {
        panel.innerHTML = `<div class="text-danger">${err.message}</div>`;
    }
}

function bookRecommendedSlot(slotName) {
    const slotData = slotsData[slotName];
    if (!slotData) {
        showToast('warning', `Slot ${slotName} hiện chưa được tải từ server.`);
        return;
    }
    handleSlotClick(slotName, slotData);
}

async function loadWalletSummary() {
    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) return;

    try {
        const res = await fetch(`${API_BASE_URL}/wallet`, {
            headers: { 'Authorization': `Bearer ${user.token}` }
        });
        const data = await res.json();
        if (res.status === 401 || res.status === 403) { handleUnauthorized(res.status); return; }
        if (!res.ok) throw new Error(data.message || 'Không tải được ví');

        document.getElementById('walletBalanceValue').textContent = formatCurrency(data.walletBalance);
        document.getElementById('membershipFeeValue').textContent = formatCurrency(data.monthlyMembershipFee);
        document.getElementById('autoRenewMembership').checked = Boolean(data.autoRenewMembership);

        const membershipText = data.membershipPlan === 'MONTHLY'
            ? `Vé tháng còn hạn đến ${new Date(data.membershipExpiry).toLocaleString('vi-VN')}${data.autoRenewMembership ? ' • Tự động gia hạn đang bật' : ''}`
            : 'Chưa có vé tháng. Mua vé tháng để được miễn phí khi check-out trong thời gian hiệu lực.';
        document.getElementById('membershipSummaryText').textContent = membershipText;

        renderWalletTransactions(data.recentTransactions || []);
    } catch (err) {
        showToast('danger', err.message);
    }
}

function renderWalletTransactions(transactions) {
    const container = document.getElementById('walletTransactionsList');
    if (!container) return;

    if (!transactions || transactions.length === 0) {
        container.innerHTML = '<div class="text-muted small">Chưa có giao dịch ví nào.</div>';
        return;
    }

    container.innerHTML = transactions.map(transaction => {
        const amountClass = Number(transaction.amount) >= 0 ? 'text-success' : 'text-danger';
        return `
            <div class="border rounded p-2 mb-2 bg-light">
                <div class="d-flex justify-content-between align-items-start">
                    <div>
                        <div class="fw-semibold small">${transaction.type}</div>
                        <div class="text-muted small">${transaction.description}</div>
                        <div class="text-muted small">${new Date(transaction.createdAt).toLocaleString('vi-VN')}</div>
                    </div>
                    <div class="text-end">
                        <div class="fw-semibold ${amountClass}">${formatCurrency(transaction.amount)}</div>
                        <div class="text-muted small">Số dư: ${formatCurrency(transaction.balanceAfter)}</div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

async function topUpWallet() {
    // UX requirement: Top-up must be linked to payment methods (MoMo/VNPay).
    // Use the selected provider; if none selected, ask user to choose.
    if (!selectedTopUpProvider) {
        showToast('warning', 'Vui lòng chọn MoMo hoặc VNPay để nạp tiền.');
        return;
    }
    await startGatewayTopUp(selectedTopUpProvider);
}

async function purchaseMembership() {
    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) return;

    try {
        const res = await fetch(`${API_BASE_URL}/wallet/membership`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${user.token}`
            },
            body: JSON.stringify({
                plan: 'MONTHLY',
                autoRenewMembership: document.getElementById('autoRenewMembership').checked
            })
        });
        const data = await res.json();
        if (res.status === 401 || res.status === 403) { handleUnauthorized(res.status); return; }
        if (!res.ok) throw new Error(data.message || 'Mua vé tháng thất bại');

        showToast('success', 'Mua/gia hạn vé tháng thành công.');
        renderWalletTransactions(data.recentTransactions || []);
        loadWalletSummary();
    } catch (err) {
        showToast('danger', err.message);
    }
}

async function withdrawWallet() {
    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) return;

    const amountInput = document.getElementById('withdrawAmount');
    const descriptionInput = document.getElementById('withdrawDescription');
    const amount = Number(amountInput.value || 0);

    if (!amount || amount <= 0) {
        showToast('warning', 'Vui lòng nhập số tiền rút hợp lệ.');
        return;
    }

    try {
        const res = await fetch(`${API_BASE_URL}/wallet/withdraw`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${user.token}`
            },
            body: JSON.stringify({ amount, description: descriptionInput.value })
        });
        const data = await res.json();
        if (res.status === 401 || res.status === 403) { handleUnauthorized(res.status); return; }
        if (!res.ok) throw new Error(data.message || 'Rút tiền thất bại');

        amountInput.value = '';
        descriptionInput.value = '';
        showToast('success', `Rút tiền thành công. Số dư mới: ${formatCurrency(data.walletBalance)}`);
        renderWalletTransactions(data.recentTransactions || []);
        loadWalletSummary();
    } catch (err) {
        showToast('danger', err.message);
    }
}

async function loadAvailableVouchers() {
    const panel = document.getElementById('voucherListPanel');
    if (!panel) return;

    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) return;

    try {
        const res = await fetch(`${API_BASE_URL}/vouchers/available`, {
            headers: { 'Authorization': `Bearer ${user.token}` }
        });
        const data = await res.json();
        if (res.status === 401 || res.status === 403) { handleUnauthorized(res.status); return; }
        if (!res.ok) throw new Error(data.message || 'Không tải được voucher');

        if (!data || data.length === 0) {
            panel.innerHTML = '<div class="text-muted small">Chưa có voucher khả dụng.</div>';
            return;
        }

        panel.innerHTML = data.map(voucher => `
            <div class="border rounded p-2 mb-2 bg-light">
                <div class="fw-semibold text-primary">${voucher.code}</div>
                <div class="small">${voucher.description}</div>
                <div class="small text-muted">Giảm: ${voucher.discountType === 'PERCENT' ? voucher.discountValue + '%' : formatCurrency(voucher.discountValue)} • Còn lại: ${voucher.remainingUses}</div>
            </div>
        `).join('');
    } catch (err) {
        panel.innerHTML = `<div class="text-danger small">${err.message}</div>`;
    }
}

async function simulateOcr() {
    const input = document.getElementById('ocrImageInput');
    const panel = document.getElementById('ocrResultPanel');
    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');

    if (!user || !user.token) return;
    if (!input?.files?.length) {
        showToast('warning', 'Vui lòng chọn ảnh xe để mô phỏng OCR.');
        return;
    }

    panel.innerHTML = '<div class="text-muted">Đang phân tích ảnh biển số...</div>';

    try {
        const formData = new FormData();
        formData.append('image', input.files[0]);

        const res = await fetch(`${API_BASE_URL}/ocr/simulate`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${user.token}` },
            body: formData
        });
        const data = await res.json();
        if (res.status === 401 || res.status === 403) { handleUnauthorized(res.status); return; }
        if (!res.ok) throw new Error(data.message || 'OCR simulation thất bại');

        panel.innerHTML = `
            <div class="alert alert-success py-2 mb-0 small">
                <div><strong>Biển số:</strong> ${data.detectedPlate}</div>
                <div><strong>Confidence:</strong> ${Math.round(Number(data.confidence || 0) * 100)}%</div>
                <div class="text-muted">${data.message}</div>
            </div>
        `;
    } catch (err) {
        panel.innerHTML = `<div class="text-danger small">${err.message}</div>`;
    }
}

/**
 * Toast notification
 */
function showToast(type, message) {
    const toastEl = document.getElementById('notifToast');
    const toastBody = document.getElementById('toastMessage');
    toastEl.className = `toast align-items-center text-bg-${type} border-0`;
    toastBody.textContent = message;
    bootstrap.Toast.getOrCreateInstance(toastEl).show();
}

/**
 * Xử lý 401: xóa token hết hạn và redirect về login
 */
function handleUnauthorized(status = 401) {
    if (unauthorizedRedirectScheduled) {
        return;
    }
    unauthorizedRedirectScheduled = true;

    localStorage.removeItem('user');
    const loginUrl = (typeof FRONTEND_LOGIN_URL !== 'undefined' && FRONTEND_LOGIN_URL)
        ? FRONTEND_LOGIN_URL : '/login';
    const numericStatus = Number(status) || 401;
    const forbidden = numericStatus === 403;
    try {
        sessionStorage.setItem('sp_auth_redirect_reason', forbidden ? 'api_403_forbidden' : 'api_401_unauthorized');
    } catch (e) {
        /* ignore */
    }
    // Đóng tất cả modal đang mở (nếu có)
    document.querySelectorAll('.modal.show').forEach(el => {
        bootstrap.Modal.getInstance(el)?.hide();
    });
    showToast('warning', forbidden
        ? '⚠️ Bạn không có quyền hoặc phiên đăng nhập không hợp lệ. Đang chuyển về trang đăng nhập...'
        : '⚠️ Phiên đăng nhập hết hạn. Đang chuyển về trang đăng nhập...');
    setTimeout(() => {
        window.location.href = loginUrl + (forbidden ? '?reason=forbidden' : '?reason=expired');
    }, 1500);
}

// ============================================
// 8. WEBSOCKET - KẾT NỐI (STOMP OVER SOCKJS)
// ============================================

function connectWebSocket() {
    // Tạo SockJS connection
    const socket = new SockJS(WS_BASE_URL);

    // Tạo STOMP client
    stompClient = Stomp.over(socket);

    // TẮT DEBUG LOG (tránh spam console)
    stompClient.debug = null;

    // Kết nối với timeout 10s
    const connectHeaders = {};
    const connectCallback = onConnected;
    const errorCallback = onError;

    stompClient.connect(connectHeaders, connectCallback, errorCallback);
}

// ============================================
// 9. WEBSOCKET - CALLBACK KHI KẾT NỐI THÀNH CÔNG
// ============================================

function onConnected() {
    // Subscribe topic để nhận update
    stompClient.subscribe('/topic/parking-updates', onMessageReceived);

    // Đồng bộ lại toàn bộ map sau khi kết nối hoặc reconnect.
    fetchAllSlots();
}

// ============================================
// 10. WEBSOCKET - CALLBACK KHI NHẬN MESSAGE
// ============================================

function onMessageReceived(payload) {
    const message = JSON.parse(payload.body);

    // Cập nhật dữ liệu local
    if (message.slotName) {
        if (message.status === 'DELETED') {
            delete slotsData[message.slotName];
        } else {
            const currentSlot = slotsData[message.slotName] || {};
            slotsData[message.slotName] = { ...currentSlot, ...message };
        }

        // Cập nhật UI
        refreshSlotElement(message.slotName);
    }
}

// ============================================
// 11. CẬP NHẬT UI SLOT (ĐỔI MÀU REAL-TIME)
// ============================================

function refreshSlotElement(slotName) {
    const slotElement = document.getElementById(`slot-${slotName}`);
    const slotData = slotsData[slotName] || createPlaceholderSlot(slotName);

    if (!slotElement) return;

    // Xóa tất cả class status cũ
    slotElement.classList.remove('slot-available', 'slot-reserved', 'slot-occupied', 'slot-maintenance');

    // Thêm class status mới
    slotElement.classList.add(getStatusClass(slotData.status));

    // Cập nhật text status
    const statusElement = slotElement.querySelector('.slot-status');
    if (statusElement) {
        statusElement.textContent = getStatusText(slotData.status);
    }

    slotElement.dataset.slotStatus = slotData.status;

}

// ============================================
// 12. WEBSOCKET - XỬ LÝ LỖI
// ============================================

function onError(error) {
    showToast('warning', 'Mất kết nối real-time. Hệ thống sẽ tự kết nối lại.');

    // Thử kết nối lại sau 5 giây
    setTimeout(connectWebSocket, 5000);
}

// ============================================
// 13. HIỂN THỊ LỖI CHO USER
// ============================================

function showError(message) {
    const gridContainer = document.getElementById('parking-grid');
    gridContainer.innerHTML = `
        <div class="alert alert-danger text-center" role="alert">
            <i class="bi bi-exclamation-triangle"></i> ${message}
        </div>
    `;
}
