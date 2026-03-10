/**
 * Frontend JavaScript cho Authentication (Login/Register)
 * Kết nối với API: POST /api/auth/login, POST /api/auth/register
 */

// ============================================
// 1. XỬ LÝ ĐĂNG NHẬP
// ============================================

document.getElementById('loginForm')?.addEventListener('submit', async function (e) {
    e.preventDefault();

    const email = document.getElementById('floatingInput').value;
    const password = document.getElementById('floatingPassword').value;
    const submitBtn = e.target.querySelector('button[type="submit"]');

    // Disable button khi đang xử lý
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang đăng nhập...';

    try {
        const response = await fetch(`${API_BASE_URL}/auth/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ email, password })
        });

        const data = await response.json();

        if (response.ok) {
            // Đăng nhập thành công
            // Lưu thông tin user vào localStorage
            localStorage.setItem('user', JSON.stringify(data));

            // Hiển thị thông báo
            showAlert('success', 'Đăng nhập thành công!');

            // Chuyển hướng đến dashboard sau 1 giây
            setTimeout(() => {
                window.location.href = (typeof FRONTEND_DASHBOARD_URL !== 'undefined' && FRONTEND_DASHBOARD_URL)
                    ? FRONTEND_DASHBOARD_URL
                    : 'dashboard.html';
            }, 1000);

        } else {
            // Đăng nhập thất bại
            throw new Error(data.message || 'Email hoặc mật khẩu không đúng');
        }

    } catch (error) {
        console.error('❌ Lỗi đăng nhập:', error);
        showAlert('danger', error.message);

        // Reset button
        submitBtn.disabled = false;
        submitBtn.textContent = 'Đăng nhập';
    }
});

// ============================================
// 1.1 GOOGLE LOGIN (OAUTH2 REDIRECT)
// ============================================

document.getElementById('googleLoginBtn')?.addEventListener('click', function (e) {
    // Defensive: avoid any unexpected form submission/navigation overrides
    e.preventDefault();
    e.stopPropagation();
    window.location.assign(
        (typeof OAUTH2_GOOGLE_AUTH_URL !== 'undefined' && OAUTH2_GOOGLE_AUTH_URL)
            ? OAUTH2_GOOGLE_AUTH_URL
            : 'http://localhost:8080/oauth2/authorization/google'
    );
});

// ============================================
// 2. XỬ LÝ ĐĂNG KÝ (Nếu có form)
// ============================================

document.getElementById('registerForm')?.addEventListener('submit', async function (e) {
    e.preventDefault();

    const fullName = document.getElementById('fullName').value;
    const email = document.getElementById('email').value;
    const password = document.getElementById('password').value;
    const submitBtn = e.target.querySelector('button[type="submit"]');

    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang đăng ký...';

    try {
        const response = await fetch(`${API_BASE_URL}/auth/register`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ fullName, email, password })
        });

        const data = await response.json();

        if (response.ok) {
            showAlert('success', 'Đăng ký thành công! Đang chuyển đến trang đăng nhập...');

            setTimeout(() => {
                window.location.href = (typeof FRONTEND_LOGIN_URL !== 'undefined' && FRONTEND_LOGIN_URL)
                    ? FRONTEND_LOGIN_URL
                    : 'login.html';
            }, 1500);

        } else {
            throw new Error(data.message || 'Đăng ký thất bại');
        }

    } catch (error) {
        console.error('❌ Lỗi đăng ký:', error);
        showAlert('danger', error.message);

        submitBtn.disabled = false;
        submitBtn.textContent = 'Đăng ký';
    }
});

// ============================================
// 3. KIỂM TRA ĐĂNG NHẬP (Dùng trong dashboard)
// ============================================

function parseJwtPayload(token) {
    if (!token || typeof token !== 'string') {
        return null;
    }

    try {
        const payloadBase64 = token.split('.')[1];
        if (!payloadBase64) {
            return null;
        }

        return JSON.parse(atob(payloadBase64.replace(/-/g, '+').replace(/_/g, '/')));
    } catch (err) {
        console.warn('⚠️ Không parse được JWT payload:', err);
        return null;
    }
}

function hasValidToken(user) {
    if (!user || !user.token) {
        return false;
    }

    const payload = parseJwtPayload(user.token);
    if (!payload || !payload.exp) {
        return false;
    }

    return Date.now() < payload.exp * 1000;
}

function getStoredUser() {
    try {
        const raw = localStorage.getItem('user');
        if (!raw) {
            return null;
        }
        return JSON.parse(raw);
    } catch (err) {
        console.warn('⚠️ localStorage user bị lỗi, xóa session cũ', err);
        localStorage.removeItem('user');
        return null;
    }
}

function clearInvalidSession() {
    localStorage.removeItem('user');
}

function hydrateUserFromOAuth2QueryParams() {
    try {
        const urlParams = new URLSearchParams(window.location.search);
        const userId = urlParams.get('userId');
        const email = urlParams.get('email');
        const fullName = urlParams.get('fullName');
        const role = urlParams.get('role');
        const branchCode = urlParams.get('branchCode');
        const avatarUrl = urlParams.get('avatarUrl');
        const token = urlParams.get('token');

        if (!userId || !email || !token) {
            return false;
        }

        const userData = {
            userId: Number(userId),
            email: email,
            fullName: typeof fullName === 'string' ? fullName : '',
            role: role || 'ROLE_USER',
            branchCode: branchCode || '',
            avatarUrl: avatarUrl || '',
            token: token,
            loginMethod: 'GOOGLE'
        };

        // Normalize display name a bit (e.g., remove trailing "_918" if present)
        userData.fullName = formatDisplayName(userData);

        if (!hasValidToken(userData)) {
            console.warn('⚠️ OAuth2 redirect trả về token không hợp lệ hoặc đã hết hạn');
            clearInvalidSession();
            return false;
        }

        localStorage.setItem('user', JSON.stringify(userData));

        // Strip query params but keep the current path (/dashboard or /dashboard.html)
        window.history.replaceState({}, document.title, window.location.pathname);

        console.log('✅ Hydrated user from Google OAuth2 redirect');
        return true;
    } catch (err) {
        console.warn('⚠️ Failed to hydrate user from OAuth2 params:', err);
        return false;
    }
}

function formatDisplayName(user) {
    const raw = (user?.fullName || '').toString().trim();
    const email = (user?.email || '').toString().trim();

    let name = raw;
    if (!name && email.includes('@')) {
        name = email.split('@')[0];
    }

    // If the name looks like "Nguyen Van A_918" or "Nguyen Van A 918", strip the trailing numeric tag
    name = name.replace(/([_\s-])\d{2,}$/u, '').trim();
    name = name.replace(/\s{2,}/g, ' ').trim();

    return name || 'User';
}

function checkAuth() {
    const user = getStoredUser();

    if (!user || !hasValidToken(user)) {
        clearInvalidSession();
        window.location.href = (typeof FRONTEND_LOGIN_URL !== 'undefined' && FRONTEND_LOGIN_URL)
            ? FRONTEND_LOGIN_URL
            : 'login.html';
        return null;
    }

    return user;
}

// ============================================
// 4. CẬP NHẬT UI USER INFO (Navbar)
// ============================================

function updateUserInfo() {
    const user = checkAuth();

    if (user) {
        // Cập nhật tên user trong navbar
        const userNameElement = document.querySelector('.navbar .text-white strong');
        if (userNameElement) {
            userNameElement.textContent = formatDisplayName(user);
        }
    }
}

// ============================================
// 5. ĐĂNG XUẤT
// ============================================

document.getElementById('btnLogout')?.addEventListener('click', function () {
    if (confirm('Bạn có chắc muốn đăng xuất?')) {
        localStorage.removeItem('user');
        window.location.href = (typeof FRONTEND_LOGIN_URL !== 'undefined' && FRONTEND_LOGIN_URL)
            ? FRONTEND_LOGIN_URL
            : 'login.html';
    }
});

// ============================================
// 6. HELPER: HIỂN THỊ THÔNG BÁO
// ============================================

function showAlert(type, message) {
    // Tìm container để hiển thị alert
    const alertContainer = document.querySelector('.form-signin') || document.querySelector('.container');

    if (!alertContainer) return;

    // Xóa alert cũ (nếu có)
    const oldAlert = alertContainer.querySelector('.alert');
    if (oldAlert) oldAlert.remove();

    // Tạo alert mới
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type} alert-dismissible fade show`;
    alertDiv.role = 'alert';
    alertDiv.innerHTML = `
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    `;

    // Chèn vào đầu container
    alertContainer.insertBefore(alertDiv, alertContainer.firstChild);

    // Tự động ẩn sau 5 giây
    setTimeout(() => {
        alertDiv.remove();
    }, 5000);
}

// ============================================
// 7. AUTO-RUN KHI TRANG LOAD
// ============================================

document.addEventListener('DOMContentLoaded', function () {
    // Nếu đã đăng nhập mà vẫn vào /login -> đẩy sang dashboard
    if (window.location.pathname.includes('login')) {
        const existingUser = getStoredUser();
        if (existingUser && hasValidToken(existingUser)) {
            window.location.replace(
                (typeof FRONTEND_DASHBOARD_URL !== 'undefined' && FRONTEND_DASHBOARD_URL)
                    ? FRONTEND_DASHBOARD_URL
                    : 'dashboard.html'
            );
            return;
        }

        if (existingUser && !hasValidToken(existingUser)) {
            clearInvalidSession();
        }
    }

    // Nếu đang ở dashboard -> Kiểm tra auth
    if (window.location.pathname.includes('dashboard')) {
        // Ensure OAuth2 redirect params are processed before auth guard
        const hydrated = hydrateUserFromOAuth2QueryParams();
        if (!hydrated) {
            const existingUser = getStoredUser();
            if (!existingUser || !hasValidToken(existingUser)) {
                clearInvalidSession();
            }
        }

        updateUserInfo();
    }
});
