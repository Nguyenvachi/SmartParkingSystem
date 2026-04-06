// Profile tab logic (Personal Info, Vehicles, Wallet shortcut, Activity shortcut, Settings/Security)

let _profileTabLoading = false;
let _profileMeCache = null;
let _profileVehiclesCache = [];

async function _parseJsonOrText(response) {
    const contentType = (response.headers.get('content-type') || '').toLowerCase();
    if (contentType.includes('application/json')) {
        return await response.json();
    }
    const text = await response.text();
    return { message: text ? String(text).slice(0, 500) : '' };
}

async function _profileApi(path, { method = 'GET', body = null } = {}) {
    const user = (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');

    if (!user || !user.token) {
        throw new Error('Bạn chưa đăng nhập.');
    }

    const headers = { 'Authorization': `Bearer ${user.token}` };
    if (method !== 'GET' && method !== 'DELETE') {
        headers['Content-Type'] = 'application/json';
    }

    const res = await fetch(`${API_BASE_URL}${path}`, {
        method,
        headers,
        body: body ? JSON.stringify(body) : null
    });

    const data = await _parseJsonOrText(res);

    if (res.status === 401 || res.status === 403) {
        if (typeof handleUnauthorized === 'function') {
            handleUnauthorized(res.status);
        }
        throw new Error(res.status === 403 ? 'Bạn không có quyền hoặc phiên đăng nhập không hợp lệ.' : 'Phiên đăng nhập hết hạn.');
    }

    if (!res.ok) {
        throw new Error(data?.message || `HTTP ${res.status}`);
    }

    return data;
}

function _setProfileTabVisible(isVisible) {
    const hint = document.getElementById('profileTabHint');
    const content = document.getElementById('profileTabContent');

    if (hint) hint.classList.toggle('d-none', isVisible);
    if (content) content.classList.toggle('d-none', !isVisible);
}

function _renderPersonalInfo(me) {
    const email = document.getElementById('profileEmail');
    if (email) email.value = me.email || '';

    const fullName = document.getElementById('profileFullName');
    if (fullName) fullName.value = me.fullName || '';

    const phone = document.getElementById('profilePhoneNumber');
    if (phone) phone.value = me.phoneNumber || '';

    const avatar = document.getElementById('profileAvatarUrl');
    if (avatar) avatar.value = me.avatarUrl || '';
}

function _renderSettings(me) {
    const emailToggle = document.getElementById('settingEmailEnabled');
    const pushToggle = document.getElementById('settingPushEnabled');

    if (emailToggle) emailToggle.checked = !!me.notificationEmailEnabled;
    if (pushToggle) pushToggle.checked = !!me.notificationPushEnabled;

    const currentPwd = document.getElementById('changeCurrentPassword');
    if (currentPwd) {
        currentPwd.placeholder = (me.authProvider === 'GOOGLE')
            ? 'Bỏ qua nếu bạn đăng nhập Google'
            : 'Nhập mật khẩu hiện tại';
    }
}

function _vehicleTypeLabel(t) {
    return t === 'CAR' ? 'Ô tô' : t === 'MOTORBIKE' ? 'Xe máy' : (t || '');
}

function _renderVehicles(vehicles) {
    const list = document.getElementById('vehicleList');
    if (!list) return;

    if (!vehicles || vehicles.length === 0) {
        list.innerHTML = '<div class="text-muted small">Chưa có phương tiện. Dùng form bên trên để thêm.</div>';
        return;
    }

    list.innerHTML = vehicles.map(v => {
        const color = (v.color || '').trim();
        return `
            <div class="border rounded p-2 bg-light-subtle mb-2">
                <div class="d-flex justify-content-between align-items-start gap-2">
                    <div>
                        <div class="fw-semibold">${v.plateNumber || ''}</div>
                        <div class="small text-muted">${_vehicleTypeLabel(v.vehicleType)}${color ? ` • ${color}` : ''}</div>
                    </div>
                    <div class="d-flex gap-2">
                        <button class="btn btn-outline-secondary btn-sm" onclick="editVehicle(${v.id})">Sửa</button>
                        <button class="btn btn-outline-danger btn-sm" onclick="deleteVehicle(${v.id})">Xóa</button>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function _renderWalletShortcut(wallet) {
    const bal = document.getElementById('profileWalletBalanceValue');
    const plan = document.getElementById('profileMembershipPlanText');

    if (bal) {
        const value = Number(wallet?.walletBalance || 0);
        bal.textContent = value.toLocaleString('vi-VN') + ' VND';
    }

    if (plan) {
        plan.textContent = wallet?.membershipPlan ? `Gói: ${wallet.membershipPlan}` : 'Chưa có gói.';
    }
}

function _renderRecentTransactions(transactions) {
    const panel = document.getElementById('profileRecentTransactions');
    if (!panel) return;

    if (!transactions || transactions.length === 0) {
        panel.textContent = 'Chưa có giao dịch.';
        return;
    }

    panel.innerHTML = transactions.slice(0, 3).map(t => {
        const amount = Number(t?.amount || 0).toLocaleString('vi-VN') + ' VND';
        const desc = (t?.description || '').toString().trim();
        return `<div>• ${t.type || ''}${desc ? `: ${desc}` : ''} (${amount})</div>`;
    }).join('');
}

function _renderRecentBookings(bookings) {
    const panel = document.getElementById('profileRecentBookings');
    if (!panel) return;

    if (!bookings || bookings.length === 0) {
        panel.innerHTML = '<div class="text-muted small">Chưa có booking.</div>';
        return;
    }

    panel.innerHTML = bookings.slice(0, 3).map(b => {
        const plate = (b.vehiclePlate || '').trim();
        return `
            <div class="border rounded p-2 bg-light-subtle mb-2">
                <div class="fw-semibold">Booking #${b.bookingId} • ${b.slotName || ''}</div>
                <div class="small text-muted">Status: ${b.status || ''}${plate ? ` • Plate: ${plate}` : ''}</div>
            </div>
        `;
    }).join('');
}

async function loadProfileTab() {
    const hint = document.getElementById('profileTabHint');
    if (_profileTabLoading) return;

    _profileTabLoading = true;
    _setProfileTabVisible(false);
    if (hint) hint.textContent = 'Đang tải hồ sơ...';

    try {
        const [me, vehicles, wallet, walletTx, bookings] = await Promise.all([
            _profileApi('/users/me'),
            _profileApi('/vehicles'),
            _profileApi('/wallet'),
            _profileApi('/wallet/transactions'),
            _profileApi('/bookings')
        ]);

        _profileMeCache = me;
        _profileVehiclesCache = vehicles || [];

        _renderPersonalInfo(me);
        _renderSettings(me);
        _renderVehicles(_profileVehiclesCache);
        _renderWalletShortcut(wallet);
        _renderRecentTransactions(walletTx || []);
        _renderRecentBookings(bookings || []);

        _setProfileTabVisible(true);
    } catch (err) {
        if (hint) hint.textContent = err?.message || String(err);
        _setProfileTabVisible(false);
    } finally {
        _profileTabLoading = false;
    }
}

async function editVehicle(id) {
    const v = (_profileVehiclesCache || []).find(x => Number(x.id) === Number(id));
    if (!v) return;

    const plate = prompt('Biển số:', v.plateNumber || '');
    if (plate === null) return;

    const type = prompt('Loại xe (CAR/MOTORBIKE):', v.vehicleType || 'MOTORBIKE');
    if (type === null) return;

    const color = prompt('Màu (optional):', v.color || '');
    if (color === null) return;

    try {
        const updated = await _profileApi(`/vehicles/${id}`, {
            method: 'PUT',
            body: {
                plateNumber: String(plate).trim(),
                vehicleType: String(type).trim(),
                color: String(color).trim() || null
            }
        });

        _profileVehiclesCache = _profileVehiclesCache.map(x => Number(x.id) === Number(id) ? updated : x);
        _renderVehicles(_profileVehiclesCache);
        if (typeof showToast === 'function') showToast('success', 'Đã cập nhật phương tiện.');
    } catch (err) {
        if (typeof showToast === 'function') showToast('danger', err?.message || String(err));
    }
}

async function deleteVehicle(id) {
    if (!confirm('Xóa phương tiện này?')) return;

    try {
        await _profileApi(`/vehicles/${id}`, { method: 'DELETE' });
        _profileVehiclesCache = (_profileVehiclesCache || []).filter(x => Number(x.id) !== Number(id));
        _renderVehicles(_profileVehiclesCache);
        if (typeof showToast === 'function') showToast('success', 'Đã xóa phương tiện.');
    } catch (err) {
        if (typeof showToast === 'function') showToast('danger', err?.message || String(err));
    }
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('profileMeForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();

        const fullName = document.getElementById('profileFullName')?.value || '';
        const phoneNumber = document.getElementById('profilePhoneNumber')?.value || '';
        const avatarUrl = document.getElementById('profileAvatarUrl')?.value || '';

        try {
            const updated = await _profileApi('/users/me', {
                method: 'PUT',
                body: {
                    fullName: String(fullName).trim(),
                    phoneNumber: String(phoneNumber).trim() || null,
                    avatarUrl: String(avatarUrl).trim() || null
                }
            });

            _profileMeCache = updated;
            _renderPersonalInfo(updated);

            try {
                const user = getStoredUser();
                if (user) {
                    user.fullName = updated.fullName || user.fullName;
                    user.avatarUrl = updated.avatarUrl || user.avatarUrl;
                    localStorage.setItem('user', JSON.stringify(user));
                    document.getElementById('navUserName').textContent = user.fullName;
                }
            } catch (_) {
                // ignore
            }

            if (typeof showToast === 'function') showToast('success', 'Đã cập nhật thông tin cá nhân.');
        } catch (err) {
            if (typeof showToast === 'function') showToast('danger', err?.message || String(err));
        }
    });

    document.getElementById('vehicleAddForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();

        const plateNumber = document.getElementById('vehiclePlateNumber')?.value || '';
        const vehicleType = document.getElementById('vehicleType')?.value || 'MOTORBIKE';
        const color = document.getElementById('vehicleColor')?.value || '';

        if (!String(plateNumber).trim()) {
            if (typeof showToast === 'function') showToast('warning', 'Biển số không hợp lệ.');
            return;
        }

        try {
            const created = await _profileApi('/vehicles', {
                method: 'POST',
                body: {
                    plateNumber: String(plateNumber).trim(),
                    vehicleType: String(vehicleType).trim(),
                    color: String(color).trim() || null
                }
            });

            _profileVehiclesCache = [created, ...(_profileVehiclesCache || [])];
            _renderVehicles(_profileVehiclesCache);

            document.getElementById('vehiclePlateNumber').value = '';
            document.getElementById('vehicleColor').value = '';

            if (typeof showToast === 'function') showToast('success', 'Đã thêm phương tiện.');
        } catch (err) {
            if (typeof showToast === 'function') showToast('danger', err?.message || String(err));
        }
    });

    document.getElementById('profileSettingsForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();

        const emailEnabled = document.getElementById('settingEmailEnabled')?.checked;
        const pushEnabled = document.getElementById('settingPushEnabled')?.checked;

        try {
            const updated = await _profileApi('/users/me/settings', {
                method: 'PUT',
                body: {
                    notificationEmailEnabled: !!emailEnabled,
                    notificationPushEnabled: !!pushEnabled
                }
            });

            _profileMeCache = updated;
            _renderSettings(updated);
            if (typeof showToast === 'function') showToast('success', 'Đã lưu cài đặt.');
        } catch (err) {
            if (typeof showToast === 'function') showToast('danger', err?.message || String(err));
        }
    });

    document.getElementById('changePasswordForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();

        const currentPassword = document.getElementById('changeCurrentPassword')?.value || '';
        const newPassword = document.getElementById('changeNewPassword')?.value || '';
        const confirmPassword = document.getElementById('changeConfirmPassword')?.value || '';

        if (String(newPassword).length < 6) {
            if (typeof showToast === 'function') showToast('warning', 'Mật khẩu mới phải từ 6 ký tự.');
            return;
        }
        if (newPassword !== confirmPassword) {
            if (typeof showToast === 'function') showToast('warning', 'Xác nhận mật khẩu không khớp.');
            return;
        }

        try {
            await _profileApi('/users/me/change-password', {
                method: 'POST',
                body: {
                    currentPassword,
                    newPassword,
                    confirmPassword
                }
            });

            document.getElementById('changeCurrentPassword').value = '';
            document.getElementById('changeNewPassword').value = '';
            document.getElementById('changeConfirmPassword').value = '';

            if (typeof showToast === 'function') showToast('success', 'Đổi mật khẩu thành công.');
        } catch (err) {
            if (typeof showToast === 'function') showToast('danger', err?.message || String(err));
        }
    });
});
