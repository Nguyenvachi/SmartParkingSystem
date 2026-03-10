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

// ============================================
// 2. KHỞI TẠO KHI TRANG LOAD
// ============================================

document.addEventListener('DOMContentLoaded', function() {
    console.log('🚀 Smart Parking Dashboard đã load');

    // Kiểm tra token hết hạn ngay khi load trang
    const redirected = checkTokenAndRedirect();
    if (redirected) return; // Đang chuyển hướng, dừng lại

    // Bước 1: Lấy dữ liệu slots ban đầu từ API
    fetchAllSlots();
    
    // Bước 2: Kết nối WebSocket
    connectWebSocket();
});

// ============================================
// 2.1 KIỂM TRA TOKEN HẾT HẠN (Client-side)
// Decode JWT payload (không cần verify signature)
// ============================================
function checkTokenAndRedirect() {
    const user = JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) {
        // Không có token -> auth.js sẽ redirect về login
        return false;
    }

    try {
        // JWT có dạng: header.payload.signature
        const payloadBase64 = user.token.split('.')[1];
        // Base64URL -> Base64 -> JSON
        const payload = JSON.parse(atob(payloadBase64.replace(/-/g, '+').replace(/_/g, '/')));
        const expMs = payload.exp * 1000; // exp là giây, cần đổi sang ms

        if (Date.now() >= expMs) {
            console.warn('⚠️ Token đã hết hạn. Đang chuyển về trang đăng nhập...');
            localStorage.removeItem('user');
            const loginUrl = (typeof FRONTEND_LOGIN_URL !== 'undefined' && FRONTEND_LOGIN_URL)
                ? FRONTEND_LOGIN_URL : '/login';
            window.location.href = loginUrl + '?reason=expired';
            return true;
        }
    } catch (e) {
        // Token không đúng định dạng JWT -> coi như không hợp lệ
        console.warn('⚠️ Token không hợp lệ, xóa và chuyển về login', e);
        localStorage.removeItem('user');
        const loginUrl = (typeof FRONTEND_LOGIN_URL !== 'undefined' && FRONTEND_LOGIN_URL)
            ? FRONTEND_LOGIN_URL : '/login';
        window.location.href = loginUrl + '?reason=invalid';
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
        console.log('✅ Đã tải', slots.length, 'slots từ server');
        
        // Lưu vào object để tra cứu nhanh
        slots.forEach(slot => {
            slotsData[slot.slotName] = slot;
        });
        
        // Render lưới
        renderParkingGrid();
        
    } catch (error) {
        console.error('❌ Lỗi khi tải slots:', error);
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
    
    console.log('🎨 Đã render lưới bãi xe');
}

// ============================================
// 5. TẠO ELEMENT CHO 1 SLOT
// ============================================

function createSlotElement(slotName) {
    const slotData = slotsData[slotName] || { status: 'AVAILABLE' };
    
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
    slotDiv.addEventListener('click', () => handleSlotClick(slotName, slotData));
    
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
        'MAINTENANCE': 'slot-maintenance'
    };
    return statusMap[status] || 'slot-available';
}

function getStatusText(status) {
    const textMap = {
        'AVAILABLE': 'Trống',
        'RESERVED': 'Đã đặt',
        'OCCUPIED': 'Đang đỗ',
        'MAINTENANCE': 'Bảo trì'
    };
    return textMap[status] || 'N/A';
}

// ============================================
// 7. XỬ LÝ CLICK SLOT (ĐẶT CHỖ)
// ============================================

function handleSlotClick(slotName, slotData) {
    if (slotData.status !== 'AVAILABLE') {
        alert(`Slot ${slotName} không khả dụng (${getStatusText(slotData.status)})`);
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
    selectedSlotData = slotData;
    document.getElementById('modalSlotName').textContent = slotName;
    document.getElementById('modalSlotType').textContent = slotData.type || 'SEDAN';
    document.getElementById('modalSlotPrice').textContent =
        (slotData.pricePerHour || 5000).toLocaleString('vi-VN') + ' VNĐ/giờ';

    const modal = new bootstrap.Modal(document.getElementById('bookingModal'));
    modal.show();
}

/**
 * Xác nhận đặt chỗ — gọi API POST /api/bookings
 * Tech Key #1: Nếu bị race condition, backend trả về lỗi "Slot vừa được đặt mất"
 */
async function confirmBooking() {
    if (!selectedSlotData) return;

    const user = JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) {
        alert('Bạn cần đăng nhập để đặt chỗ!');
        window.location.href = '/login';
        return;
    }

    const btn = document.getElementById('btnConfirmBooking');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang đặt...';

    try {
        const res = await fetch(`http://localhost:8080/api/bookings`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${user.token}`
            },
            body: JSON.stringify({ slotId: selectedSlotData.id })
        });

        const data = await res.json();

        if (res.status === 401) {
            handleUnauthorized();
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
    document.getElementById('qrExpiry').textContent =
        new Date(bookingData.expiryTime).toLocaleTimeString('vi-VN');

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

    // Gắn bookingId vào nút check-in
    document.getElementById('btnCheckIn').dataset.bookingId = bookingData.bookingId;

    const qrModal = new bootstrap.Modal(document.getElementById('qrModal'));
    qrModal.show();
}

/**
 * Check-in vào bãi xe — POST /api/bookings/{id}/checkin
 */
async function doCheckIn(bookingId) {
    const user = JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) return;

    const btn = document.getElementById('btnCheckIn');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang vào bãi...';

    try {
        const res = await fetch(`http://localhost:8080/api/bookings/${bookingId}/checkin`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${user.token}` }
        });
        const data = await res.json();
        if (res.status === 401) { handleUnauthorized(); return; }
        if (!res.ok) throw new Error(data.message || 'Check-in thất bại');

        bootstrap.Modal.getInstance(document.getElementById('qrModal')).hide();
        showToast('success', `✅ Check-in thành công! Slot ${data.slotName} đang đỗ.`);
        loadBookingHistory();

    } catch (err) {
        showToast('danger', err.message);
        btn.disabled = false;
        btn.innerHTML = '<i class="bi bi-box-arrow-in-right me-1"></i>Check-in Vào bãi';
    }
}

/**
 * Hủy booking — DELETE /api/bookings/{id}
 */
async function cancelBooking(bookingId) {
    if (!confirm('Bạn có chắc muốn hủy booking này?')) return;
    const user = JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) return;

    try {
        const res = await fetch(`http://localhost:8080/api/bookings/${bookingId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${user.token}` }
        });
        const data = await res.json();
        if (res.status === 401) { handleUnauthorized(); return; }
        if (!res.ok) throw new Error(data.message || 'Hủy thất bại');

        showToast('warning', `Đã hủy booking #${bookingId}. Slot được trả về trạng thái trống.`);
        loadBookingHistory();

    } catch (err) {
        showToast('danger', err.message);
    }
}

/**
 * Load lịch sử booking của user — GET /api/bookings
 */
async function loadBookingHistory() {
    const user = JSON.parse(localStorage.getItem('user') || 'null');
    if (!user || !user.token) return;

    const container = document.getElementById('bookingHistoryList');
    container.innerHTML = '<p class="text-muted text-center small py-2">Đang tải...</p>';

    try {
        const res = await fetch('http://localhost:8080/api/bookings', {
            headers: { 'Authorization': `Bearer ${user.token}` }
        });
        const bookings = await res.json();
        if (res.status === 401) {
            container.innerHTML = '<p class="text-warning small text-center">Phiên đăng nhập hết hạn. <a href="/login">Đăng nhập lại</a></p>';
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
                    </div>
                    <div class="d-flex flex-column gap-1">
                        ${b.status === 'PENDING' ? `
                            <button class="btn btn-xs btn-success py-0 px-1" onclick="doCheckIn(${b.bookingId})" title="Check-in">
                                <i class="bi bi-box-arrow-in-right"></i>
                            </button>
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

function getBookingCardClass(status) {
    return { PENDING: 'bg-warning bg-opacity-10', CHECKED_IN: 'bg-primary bg-opacity-10',
             COMPLETED: 'bg-success bg-opacity-10', CANCELLED: 'bg-secondary bg-opacity-10' }[status] || '';
}

function getStatusBadge(status) {
    return { PENDING: 'bg-warning text-dark', CHECKED_IN: 'bg-primary',
             COMPLETED: 'bg-success', CANCELLED: 'bg-secondary' }[status] || 'bg-secondary';
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
function handleUnauthorized() {
    localStorage.removeItem('user');
    const loginUrl = (typeof FRONTEND_LOGIN_URL !== 'undefined' && FRONTEND_LOGIN_URL)
        ? FRONTEND_LOGIN_URL : '/login';
    // Đóng tất cả modal đang mở (nếu có)
    document.querySelectorAll('.modal.show').forEach(el => {
        bootstrap.Modal.getInstance(el)?.hide();
    });
    showToast('warning', '⚠️ Phiên đăng nhập hết hạn. Đang chuyển về trang đăng nhập...');
    setTimeout(() => { window.location.href = loginUrl + '?reason=expired'; }, 1500);
}

// ============================================
// 8. WEBSOCKET - KẾT NỐI (STOMP OVER SOCKJS)
// ============================================

function connectWebSocket() {
    console.log('🔌 Đang kết nối WebSocket...');
    
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
    console.log('✅ WebSocket đã kết nối!');
    
    // Subscribe topic để nhận update
    stompClient.subscribe('/topic/parking-updates', onMessageReceived);
}

// ============================================
// 10. WEBSOCKET - CALLBACK KHI NHẬN MESSAGE
// ============================================

function onMessageReceived(payload) {
    const message = JSON.parse(payload.body);
    console.log('📨 Nhận được update:', message);
    
    // Cập nhật dữ liệu local
    if (message.slotName) {
        slotsData[message.slotName] = message;
        
        // Cập nhật UI
        updateSlotUI(message.slotName, message.status);
    }
}

// ============================================
// 11. CẬP NHẬT UI SLOT (ĐỔI MÀU REAL-TIME)
// ============================================

function updateSlotUI(slotName, newStatus) {
    const slotElement = document.getElementById(`slot-${slotName}`);
    
    if (!slotElement) {
        console.warn('⚠️ Không tìm thấy slot element:', slotName);
        return;
    }
    
    // Xóa tất cả class status cũ
    slotElement.classList.remove('slot-available', 'slot-reserved', 'slot-occupied', 'slot-maintenance');
    
    // Thêm class status mới
    slotElement.classList.add(getStatusClass(newStatus));
    
    // Cập nhật text status
    const statusElement = slotElement.querySelector('.slot-status');
    if (statusElement) {
        statusElement.textContent = getStatusText(newStatus);
    }
    
    console.log(`🎨 Đã cập nhật slot ${slotName} -> ${newStatus}`);
}

// ============================================
// 12. WEBSOCKET - XỬ LÝ LỖI
// ============================================

function onError(error) {
    console.error('❌ Lỗi WebSocket:', error);
    showError('Mất kết nối real-time. Đang thử kết nối lại...');
    
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
