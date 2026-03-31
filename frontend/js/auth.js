/**
 * Frontend JavaScript cho Authentication (Login/Register)
 * Kết nối với API: POST /api/auth/login, POST /api/auth/register
 */

// Nếu đã đăng nhập (token còn hạn) thì không ở lại trang login/register.
document.addEventListener('DOMContentLoaded', function () {
    try {
        const params = new URLSearchParams(window.location.search || '');
        // Useful for role testing: keep auth page even if a previous token is still valid.
        if (params.get('logout') === '1') {
            localStorage.removeItem('user');
            return;
        }
        if (params.get('force') === '1') {
            return;
        }

        const path = (window.location.pathname || '').toLowerCase();
        const isAuthPage = path.endsWith('/login') || path.endsWith('/login.html')
            || path.endsWith('/register') || path.endsWith('/register.html');

        // OAuth2 flow now returns to login page with token query params.
        if (isAuthPage && typeof hydrateUserFromOAuth2QueryParams === 'function') {
            try {
                const hydrated = hydrateUserFromOAuth2QueryParams();
                if (hydrated) {
                    const hydratedUser = getStoredUser();
                    if (hydratedUser && hasValidToken(hydratedUser)) {
                        const dashboardUrl = (typeof FRONTEND_DASHBOARD_URL !== 'undefined' && FRONTEND_DASHBOARD_URL)
                            ? FRONTEND_DASHBOARD_URL
                            : (path.endsWith('/register') || path.endsWith('/login')) ? '/dashboard' : 'dashboard.html';
                        window.location.replace(dashboardUrl);
                        return;
                    }
                }
            } catch (e) {
                // ignore and continue existing flow
            }
        }

        const user = getStoredUser();
        if (!user || !hasValidToken(user)) {
            return;
        }

        if (!isAuthPage) {
            return;
        }

        const dashboardUrl = (typeof FRONTEND_DASHBOARD_URL !== 'undefined' && FRONTEND_DASHBOARD_URL)
            ? FRONTEND_DASHBOARD_URL
            : (path.endsWith('/register') || path.endsWith('/login')) ? '/dashboard' : 'dashboard.html';
        window.location.replace(dashboardUrl);
    } catch (e) {
        // ignore
    }
});

// ============================================
// 1. XỬ LÝ ĐĂNG NHẬP
// ============================================

function setAuthMode(mode) {
    const loginForm = document.getElementById('loginForm');
    const forgotForm = document.getElementById('forgotPasswordForm');
    const resetForm = document.getElementById('resetPasswordForm');

    if (!loginForm || !forgotForm || !resetForm) {
        return;
    }

    loginForm.classList.toggle('d-none', mode !== 'login');
    forgotForm.classList.toggle('d-none', mode !== 'forgot');
    resetForm.classList.toggle('d-none', mode !== 'reset');
}

document.getElementById('btnShowForgotPassword')?.addEventListener('click', function () {
    const email = document.getElementById('floatingInput')?.value || '';
    const forgotEmail = document.getElementById('forgotEmail');
    if (forgotEmail && email) {
        forgotEmail.value = email;
    }
    setAuthMode('forgot');
});

document.getElementById('btnBackToLoginFromForgot')?.addEventListener('click', function () {
    setAuthMode('login');
});

document.getElementById('btnShowResetPassword')?.addEventListener('click', function () {
    const forgotEmail = document.getElementById('forgotEmail')?.value || '';
    const resetEmail = document.getElementById('resetEmail');
    if (resetEmail && forgotEmail) {
        resetEmail.value = forgotEmail;
    }
    setAuthMode('reset');
});

document.getElementById('btnBackToLoginFromReset')?.addEventListener('click', function () {
    setAuthMode('login');
});

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
// 2.1 QUÊN MẬT KHẨU / RESET MẬT KHẨU
// ============================================

document.getElementById('forgotPasswordForm')?.addEventListener('submit', async function (e) {
    e.preventDefault();

    const email = document.getElementById('forgotEmail')?.value || '';
    const submitBtn = e.target.querySelector('button[type="submit"]');

    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang gửi...';
    }

    try {
        const response = await fetch(`${API_BASE_URL}/auth/forgot-password`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email })
        });

        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.message || 'Không thể tạo yêu cầu reset mật khẩu.');
        }

        showAlert('success', data.message || 'Đã tạo yêu cầu reset mật khẩu.');

        // Demo/dev: nếu backend trả resetToken, tự chuyển qua form reset.
        if (data.resetToken) {
            const resetEmail = document.getElementById('resetEmail');
            const resetToken = document.getElementById('resetToken');
            if (resetEmail) resetEmail.value = email;
            if (resetToken) resetToken.value = data.resetToken;
            setAuthMode('reset');
        }
    } catch (error) {
        console.error('❌ Lỗi forgot-password:', error);
        showAlert('danger', error.message);
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Gửi yêu cầu reset';
        }
    }
});

document.getElementById('resetPasswordForm')?.addEventListener('submit', async function (e) {
    e.preventDefault();

    const email = document.getElementById('resetEmail')?.value || '';
    const token = document.getElementById('resetToken')?.value || '';
    const newPassword = document.getElementById('resetNewPassword')?.value || '';
    const confirmPassword = document.getElementById('resetConfirmPassword')?.value || '';
    const submitBtn = e.target.querySelector('button[type="submit"]');

    if (newPassword !== confirmPassword) {
        showAlert('danger', 'Mật khẩu xác nhận không khớp.');
        return;
    }

    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang đặt lại...';
    }

    try {
        const response = await fetch(`${API_BASE_URL}/auth/reset-password`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, token, newPassword, confirmPassword })
        });
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.message || 'Đặt lại mật khẩu thất bại.');
        }

        showAlert('success', data.message || 'Đặt lại mật khẩu thành công.');
        setAuthMode('login');
    } catch (error) {
        console.error('❌ Lỗi reset-password:', error);
        showAlert('danger', error.message);
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Đặt lại mật khẩu';
        }
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
        const payloadBase64Url = token.split('.')[1];
        if (!payloadBase64Url) {
            return null;
        }

        // JWT uses Base64URL (RFC 7515). Browser atob expects standard Base64 with padding.
        const normalized = payloadBase64Url
            .replace(/-/g, '+')
            .replace(/_/g, '/');

        const paddingNeeded = (4 - (normalized.length % 4)) % 4;
        const payloadBase64 = normalized + '='.repeat(paddingNeeded);

        const decoded = atob(payloadBase64);
        return JSON.parse(decoded);
    } catch (err) {
        console.warn('⚠️ Không parse được JWT payload:', err);
        return null;
    }
}

function hasValidToken(user) {
    if (!user || !user.token) {
        return false;
    }

    const tokenFormat = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;
    if (!tokenFormat.test(user.token)) {
        return false;
    }

    const payload = parseJwtPayload(user.token);
    if (!payload || !payload.exp) {
        // Fallback: keep session for server-side verification if payload decode fails unexpectedly.
        // This avoids false logout loops right after OAuth2 redirect.
        return true;
    }

    // Allow a small client clock skew to avoid false-expired redirects.
    const clockSkewMs = 5000;
    return Date.now() < (payload.exp * 1000 + clockSkewMs);
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
        const queryParams = new URLSearchParams(window.location.search || '');
        const hashRaw = (window.location.hash || '').replace(/^#/, '');
        const hashParams = new URLSearchParams(hashRaw);

        const getParam = (key) => queryParams.get(key) || hashParams.get(key);

        const userId = getParam('userId');
        const email = getParam('email');
        const fullName = getParam('fullName');
        const role = getParam('role');
        const branchCode = getParam('branchCode');
        const avatarUrl = getParam('avatarUrl');
        const token = getParam('token');

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

        // Strip query/hash params but keep the current path
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
    const alertContainer = document.querySelector('.auth-card .card-body')
        || document.querySelector('.form-signin')
        || document.querySelector('.container');

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
