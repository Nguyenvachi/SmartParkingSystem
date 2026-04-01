/**
 * API Service Layer - Centralized API calls
 * Tất cả các request đến Backend đều đi qua file này
 */

const API_CONFIG = {
    // Prefer shared config from config.js when available.
    BASE_URL: (typeof API_BASE_URL !== 'undefined' && API_BASE_URL) ? API_BASE_URL : `${window.location.origin}/api`,
    TIMEOUT: 10000 // 10 giây
};

/**
 * Generic fetch wrapper với error handling
 */
async function apiRequest(endpoint, options = {}) {
    const url = `${API_CONFIG.BASE_URL}${endpoint}`;
    
    const defaultHeaders = {
        'Content-Type': 'application/json'
    };
    
    // Thêm Authorization header nếu user đã đăng nhập
    const user = JSON.parse(localStorage.getItem('user') || 'null');
    if (user && user.token) {
        defaultHeaders['Authorization'] = `Bearer ${user.token}`;
    }
    
    const config = {
        ...options,
        headers: {
            ...defaultHeaders,
            ...options.headers
        }
    };
    
    try {
        const response = await fetch(url, config);
        const contentType = (response.headers.get('content-type') || '').toLowerCase();
        let data = null;
        if (contentType.includes('application/json')) {
            data = await response.json();
        } else {
            const text = await response.text();
            // Nginx 502/503 often returns HTML; keep it readable.
            data = { message: text ? String(text).slice(0, 500) : '' };
        }
        
        if (!response.ok) {
            const hint = (response.status === 502 || response.status === 503)
                ? ' (backend/proxy đang lỗi hoặc backend chưa chạy)'
                : '';
            throw new Error((data && data.message ? data.message : `HTTP error! status: ${response.status}`) + hint);
        }
        
        return { success: true, data };
        
    } catch (error) {
        console.error(`❌ API Error [${endpoint}]:`, error);
        return { success: false, error: error.message };
    }
}

/**
 * ============================================
 * AUTHENTICATION APIs
 * ============================================
 */

export const AuthAPI = {
    /**
     * Đăng ký user mới
     */
    register: async (fullName, email, password) => {
        return await apiRequest('/auth/register', {
            method: 'POST',
            body: JSON.stringify({ fullName, email, password })
        });
    },
    
    /**
     * Đăng nhập
     */
    login: async (email, password) => {
        return await apiRequest('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        });
    }
};

/**
 * ============================================
 * PARKING SLOT APIs
 * ============================================
 */

export const SlotAPI = {
    /**
     * Lấy tất cả slots (có thể filter theo status)
     */
    getAll: async (status = null) => {
        const queryParam = status ? `?status=${status}` : '';
        return await apiRequest(`/slots${queryParam}`, {
            method: 'GET'
        });
    },
    
    /**
     * Lấy thông tin 1 slot theo ID
     */
    getById: async (slotId) => {
        return await apiRequest(`/slots/${slotId}`, {
            method: 'GET'
        });
    },
    
    /**
     * Tạo slot mới (Admin only)
     */
    create: async (slotData) => {
        return await apiRequest('/slots', {
            method: 'POST',
            body: JSON.stringify(slotData)
        });
    },
    
    /**
     * Cập nhật slot (Admin only)
     */
    update: async (slotId, slotData) => {
        return await apiRequest(`/slots/${slotId}`, {
            method: 'PUT',
            body: JSON.stringify(slotData)
        });
    },
    
    /**
     * Xóa slot (Admin only)
     */
    delete: async (slotId) => {
        return await apiRequest(`/slots/${slotId}`, {
            method: 'DELETE'
        });
    }
};

/**
 * ============================================
 * HELPER FUNCTIONS
 * ============================================
 */

/**
 * Kiểm tra xem user đã đăng nhập hay chưa
 */
export function isAuthenticated() {
    return localStorage.getItem('user') !== null;
}

/**
 * Lấy thông tin user hiện tại
 */
export function getCurrentUser() {
    const user = localStorage.getItem('user');
    return user ? JSON.parse(user) : null;
}

/**
 * Đăng xuất
 */
export function logout() {
    localStorage.removeItem('user');
    window.location.href = 'login.html';
}

/**
 * ============================================
 * BOOKING APIs — Phase 3
 * Tech Key #1: Optimistic Locking (Backend xử lý, FE nhận lỗi conflict)
 * Tech Key #7: QR Code (Backend trả về qrCodeBase64, FE render <img>)
 * ============================================
 */
const BookingAPI = {
    /**
     * Tạo booking mới — POST /api/bookings
     * Cần JWT token (Authorization: Bearer ...)
     * @param {number} slotId
     */
    create: async (slotId) => {
        return await apiRequest('/bookings', {
            method: 'POST',
            body: JSON.stringify({ slotId })
        });
    },

    /**
     * Lấy lịch sử booking của user đang đăng nhập — GET /api/bookings
     */
    getMyBookings: async () => {
        return await apiRequest('/bookings', { method: 'GET' });
    },

    /**
     * Lấy chi tiết 1 booking (có QR Code) — GET /api/bookings/{id}
     * @param {number} bookingId
     */
    getById: async (bookingId) => {
        return await apiRequest(`/bookings/${bookingId}`, { method: 'GET' });
    },

    /**
     * Check-in vào bãi xe — POST /api/bookings/{id}/checkin
     * @param {number} bookingId
     */
    checkIn: async (bookingId) => {
        return await apiRequest(`/bookings/${bookingId}/checkin`, { method: 'POST' });
    },

    /**
     * Check-out ra bãi và thanh toán bằng ví
     */
    checkOut: async (bookingId, voucherCode = '') => {
        return await apiRequest(`/bookings/${bookingId}/checkout`, {
            method: 'POST',
            body: JSON.stringify({ voucherCode })
        });
    },

    /**
     * Hủy booking — DELETE /api/bookings/{id}
     * Chỉ được hủy khi status = PENDING
     * @param {number} bookingId
     */
    cancel: async (bookingId) => {
        return await apiRequest(`/bookings/${bookingId}`, { method: 'DELETE' });
    }
};

const WalletAPI = {
    getSummary: async () => {
        return await apiRequest('/wallet', { method: 'GET' });
    },

    getTransactions: async () => {
        return await apiRequest('/wallet/transactions', { method: 'GET' });
    },

    topUp: async (amount, description = '') => {
        return await apiRequest('/wallet/top-up', {
            method: 'POST',
            body: JSON.stringify({ amount, description })
        });
    },

    withdraw: async (amount, description = '') => {
        return await apiRequest('/wallet/withdraw', {
            method: 'POST',
            body: JSON.stringify({ amount, description })
        });
    },

    purchaseMembership: async (autoRenewMembership = false) => {
        return await apiRequest('/wallet/membership', {
            method: 'POST',
            body: JSON.stringify({ plan: 'MONTHLY', autoRenewMembership })
        });
    }
};

SlotAPI.getRecommendation = async (vehicleType = null) => {
    const queryParam = vehicleType ? `?vehicleType=${vehicleType}` : '';
    return await apiRequest(`/slots/recommendation${queryParam}`, {
        method: 'GET'
    });
};

const VoucherAPI = {
    getAvailable: async () => {
        return await apiRequest('/vouchers/available', { method: 'GET' });
    }
};

const OcrAPI = {
    simulate: async (file) => {
        const formData = new FormData();
        formData.append('image', file);

        const user = getStoredUser();
        const response = await fetch(`${API_BASE_URL}/ocr/simulate`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${user?.token || ''}`
            },
            body: formData
        });

        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.message || 'OCR simulation thất bại');
        }
        return data;
    }
};
