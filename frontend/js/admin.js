let adminSlotsCache = [];
let adminBlacklistCache = [];
let adminUsersCache = [];

const adminUsersQuery = {
    q: '',
    page: 0,
    size: 20,
    sortBy: 'createdAt',
    sortDir: 'desc',
    totalPages: 1
};

document.addEventListener('DOMContentLoaded', () => {
    initializeAdminConsole();
});

function getDashboardUser() {
    return (typeof getStoredUser === 'function')
        ? getStoredUser()
        : JSON.parse(localStorage.getItem('user') || 'null');
}

function isAdminUser(user) {
    return Boolean(user && (user.role === 'ROLE_ADMIN' || user.role === 'ROLE_BRANCH_ADMIN'));
}

function isGlobalAdminUser(user) {
    return Boolean(user && user.role === 'ROLE_ADMIN');
}

function normalizedBranchCode(branchCode) {
    return (branchCode || '').trim().toUpperCase();
}

async function adminApiRequest(endpoint, options = {}) {
    const user = getDashboardUser();
    if (!user || !user.token) {
        throw new Error('Bạn chưa đăng nhập.');
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${user.token}`,
            ...(options.headers || {})
        }
    });

    const contentType = response.headers.get('content-type') || '';
    const data = contentType.includes('application/json') ? await response.json() : null;

    if (response.status === 401) {
        handleUnauthorized();
        throw new Error('Phiên đăng nhập hết hạn.');
    }

    if (!response.ok) {
        throw new Error(data?.message || 'Yêu cầu quản trị thất bại.');
    }

    return data;
}

async function initializeAdminConsole() {
    const user = getDashboardUser();
    if (!isAdminUser(user)) {
        return;
    }

    document.getElementById('navAdminItem')?.classList.remove('d-none');
    document.getElementById('adminConsoleSection')?.classList.remove('d-none');
    document.getElementById('adminRoleBadge').textContent = formatRoleLabel(user.role);
    document.getElementById('adminBranchBadge').textContent = isGlobalAdminUser(user)
        ? 'TẤT CẢ CHI NHÁNH'
        : `CHI NHÁNH ${normalizedBranchCode(user.branchCode) || 'MAIN'}`;

    if (!isGlobalAdminUser(user)) {
        document.querySelectorAll('.super-admin-only').forEach(element => element.remove());
    }

    bindAdminEvents(user);
    presetAdminBranchInputs(user);

    await loadAdminSummary();
    await loadAdminSlots();
    await loadAdminBlacklist();

    if (isGlobalAdminUser(user)) {
        await loadAdminUsers();
        await loadAdminAuditLogs();
    }
}

function bindAdminEvents(user) {
    const slotForm = document.getElementById('adminSlotForm');
    if (slotForm && !slotForm.dataset.bound) {
        slotForm.dataset.bound = 'true';
        slotForm.addEventListener('submit', async event => {
            event.preventDefault();
            await submitAdminSlotForm();
        });
    }

    const blacklistForm = document.getElementById('adminBlacklistForm');
    if (blacklistForm && !blacklistForm.dataset.bound) {
        blacklistForm.dataset.bound = 'true';
        blacklistForm.addEventListener('submit', async event => {
            event.preventDefault();
            await submitAdminBlacklistForm();
        });
    }

    const branchInput = document.getElementById('adminSlotBranchCode');
    if (branchInput && !isGlobalAdminUser(user)) {
        branchInput.disabled = true;
    }

    const blacklistBranchInput = document.getElementById('adminBlacklistBranchCode');
    if (blacklistBranchInput && !isGlobalAdminUser(user)) {
        blacklistBranchInput.disabled = true;
    }
}

function presetAdminBranchInputs(user) {
    const branchCode = normalizedBranchCode(user.branchCode) || 'MAIN';
    const slotBranchInput = document.getElementById('adminSlotBranchCode');
    const blacklistBranchInput = document.getElementById('adminBlacklistBranchCode');

    if (slotBranchInput && !isGlobalAdminUser(user)) {
        slotBranchInput.value = branchCode;
    }

    if (blacklistBranchInput && !isGlobalAdminUser(user)) {
        blacklistBranchInput.value = branchCode;
    }
}

function scrollToAdminConsole() {
    document.getElementById('adminConsoleSection')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    const trigger = document.querySelector('[data-bs-target="#adminOverviewPane"]');
    if (trigger) {
        bootstrap.Tab.getOrCreateInstance(trigger).show();
    }
}

function formatRoleLabel(role) {
    const mapping = {
        ROLE_ADMIN: 'Admin tổng',
        ROLE_BRANCH_ADMIN: 'Admin chi nhánh',
        ROLE_USER: 'User'
    };
    return mapping[role] || role;
}

async function loadAdminSummary() {
    try {
        const summary = await adminApiRequest('/admin/summary', { method: 'GET' });
        document.getElementById('summaryUsersCount').textContent = summary.totalVisibleUsers;
        document.getElementById('summarySlotsCount').textContent = summary.totalVisibleSlots;
        document.getElementById('summaryAvailableCount').textContent = summary.availableSlots;
        document.getElementById('summaryReservedCount').textContent = summary.reservedSlots;
        document.getElementById('summaryOccupiedCount').textContent = summary.occupiedSlots;
        document.getElementById('summaryBlacklistCount').textContent = summary.activeBlacklistEntries;
        document.getElementById('adminScopeNotice').textContent = summary.globalAdmin
            ? 'Bạn đang ở chế độ Admin tổng: xem và phân quyền toàn hệ thống.'
            : `Bạn đang ở chế độ Admin chi nhánh ${normalizedBranchCode(summary.branchCode) || 'MAIN'}: chỉ quản lý dữ liệu thuộc chi nhánh của mình.`;
    } catch (error) {
        document.getElementById('adminScopeNotice').textContent = error.message;
    }
}

async function loadAdminSlots() {
    const tbody = document.getElementById('adminSlotsTableBody');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">Đang tải slot...</td></tr>';
    try {
        adminSlotsCache = await adminApiRequest('/slots', { method: 'GET' });
        if (!adminSlotsCache.length) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">Chưa có slot nào.</td></tr>';
            return;
        }

        tbody.innerHTML = adminSlotsCache.map(slot => `
            <tr>
                <td><strong>${slot.slotName}</strong></td>
                <td>${slot.type}</td>
                <td><span class="badge ${slotStatusBadgeClass(slot.status)}">${slot.status}</span></td>
                <td>${formatCurrency(slot.pricePerHour)}</td>
                <td>${slot.branchCode || 'MAIN'}</td>
                <td class="text-end">
                    <button class="btn btn-outline-primary btn-sm me-1" onclick="editAdminSlot(${slot.id})">Sửa</button>
                    <button class="btn btn-outline-danger btn-sm" onclick="deleteAdminSlot(${slot.id})">Xóa</button>
                </td>
            </tr>
        `).join('');
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center text-danger py-3">${error.message}</td></tr>`;
    }
}

function slotStatusBadgeClass(status) {
    return {
        AVAILABLE: 'bg-success',
        RESERVED: 'bg-warning text-dark',
        OCCUPIED: 'bg-danger',
        MAINTENANCE: 'bg-secondary'
    }[status] || 'bg-secondary';
}

async function submitAdminSlotForm() {
    const user = getDashboardUser();
    const slotId = document.getElementById('adminSlotId').value;
    const payload = {
        slotName: document.getElementById('adminSlotName').value.trim().toUpperCase(),
        type: document.getElementById('adminSlotType').value,
        status: document.getElementById('adminSlotStatus').value,
        pricePerHour: Number(document.getElementById('adminSlotPrice').value || 0),
        branchCode: isGlobalAdminUser(user)
            ? document.getElementById('adminSlotBranchCode').value.trim().toUpperCase()
            : normalizedBranchCode(user.branchCode)
    };

    if (!payload.slotName) {
        showToast('warning', 'Tên slot không được để trống.');
        return;
    }

    const endpoint = slotId ? `/slots/${slotId}` : '/slots';
    const method = slotId ? 'PUT' : 'POST';

    try {
        await adminApiRequest(endpoint, {
            method,
            body: JSON.stringify(payload)
        });
        showToast('success', slotId ? 'Cập nhật slot thành công.' : 'Tạo slot thành công.');
        resetAdminSlotForm();
        await loadAdminSlots();
        await loadAdminSummary();
        fetchAllSlots();
    } catch (error) {
        showToast('danger', error.message);
    }
}

function editAdminSlot(slotId) {
    const slot = adminSlotsCache.find(item => item.id === slotId);
    if (!slot) return;

    document.getElementById('adminSlotId').value = slot.id;
    document.getElementById('adminSlotName').value = slot.slotName;
    document.getElementById('adminSlotType').value = slot.type;
    document.getElementById('adminSlotStatus').value = slot.status;
    document.getElementById('adminSlotPrice').value = slot.pricePerHour;
    document.getElementById('adminSlotBranchCode').value = slot.branchCode || 'MAIN';
    document.getElementById('adminSlotFormTitle').textContent = `Cập nhật slot ${slot.slotName}`;
    document.getElementById('adminSlotSubmitBtn').textContent = 'Cập nhật slot';

    scrollToAdminConsole();
    const trigger = document.querySelector('[data-bs-target="#adminSlotsPane"]');
    if (trigger) {
        bootstrap.Tab.getOrCreateInstance(trigger).show();
    }
}

function resetAdminSlotForm() {
    const user = getDashboardUser();
    document.getElementById('adminSlotId').value = '';
    document.getElementById('adminSlotForm').reset();
    document.getElementById('adminSlotFormTitle').textContent = 'Tạo slot mới';
    document.getElementById('adminSlotSubmitBtn').textContent = 'Lưu slot';
    if (!isGlobalAdminUser(user)) {
        document.getElementById('adminSlotBranchCode').value = normalizedBranchCode(user.branchCode) || 'MAIN';
    }
}

async function deleteAdminSlot(slotId) {
    const slot = adminSlotsCache.find(item => item.id === slotId);
    if (!slot) return;
    if (!confirm(`Xóa slot ${slot.slotName}?`)) return;

    try {
        await adminApiRequest(`/slots/${slotId}`, { method: 'DELETE' });
        showToast('warning', `Đã xóa slot ${slot.slotName}.`);
        await loadAdminSlots();
        await loadAdminSummary();
        fetchAllSlots();
    } catch (error) {
        showToast('danger', error.message);
    }
}

async function loadAdminBlacklist() {
    const tbody = document.getElementById('adminBlacklistTableBody');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Đang tải blacklist...</td></tr>';
    try {
        adminBlacklistCache = await adminApiRequest('/blacklist', { method: 'GET' });
        if (!adminBlacklistCache.length) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Chưa có xe nào trong blacklist.</td></tr>';
            return;
        }

        tbody.innerHTML = adminBlacklistCache.map(entry => `
            <tr>
                <td><strong>${entry.plateNumber}</strong></td>
                <td>${entry.branchCode || 'ALL'}</td>
                <td>${entry.reason || 'Không có'}</td>
                <td>${entry.createdBy || 'system'}</td>
                <td class="text-end"><button class="btn btn-outline-danger btn-sm" onclick="deactivateBlacklistEntry(${entry.id})">Gỡ</button></td>
            </tr>
        `).join('');
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center text-danger py-3">${error.message}</td></tr>`;
    }
}

async function submitAdminBlacklistForm() {
    const user = getDashboardUser();
    const payload = {
        plateNumber: document.getElementById('adminBlacklistPlate').value.trim().toUpperCase().replace(/[^A-Z0-9]/g, ''),
        branchCode: isGlobalAdminUser(user)
            ? document.getElementById('adminBlacklistBranchCode').value.trim().toUpperCase()
            : normalizedBranchCode(user.branchCode),
        reason: document.getElementById('adminBlacklistReason').value.trim()
    };

    if (!payload.plateNumber) {
        showToast('warning', 'Biển số không được để trống.');
        return;
    }

    try {
        await adminApiRequest('/blacklist', {
            method: 'POST',
            body: JSON.stringify(payload)
        });
        showToast('success', `Đã thêm xe ${payload.plateNumber} vào blacklist.`);
        document.getElementById('adminBlacklistForm').reset();
        presetAdminBranchInputs(user);
        await loadAdminBlacklist();
        await loadAdminSummary();
    } catch (error) {
        showToast('danger', error.message);
    }
}

async function deactivateBlacklistEntry(id) {
    const entry = adminBlacklistCache.find(item => item.id === id);
    if (!entry) return;
    if (!confirm(`Gỡ xe ${entry.plateNumber} khỏi blacklist?`)) return;

    try {
        await adminApiRequest(`/blacklist/${id}`, { method: 'DELETE' });
        showToast('warning', `Đã gỡ xe ${entry.plateNumber} khỏi blacklist.`);
        await loadAdminBlacklist();
        await loadAdminSummary();
    } catch (error) {
        showToast('danger', error.message);
    }
}

async function loadAdminUsers() {
    const tbody = document.getElementById('adminUsersTableBody');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-3">Đang tải user...</td></tr>';
    try {
        const keyword = (document.getElementById('adminUserSearchInput')?.value || '').trim();
        const sortBy = document.getElementById('adminUserSortBy')?.value || adminUsersQuery.sortBy;
        const sortDir = document.getElementById('adminUserSortDir')?.value || adminUsersQuery.sortDir;

        if (keyword !== adminUsersQuery.q || sortBy !== adminUsersQuery.sortBy || sortDir !== adminUsersQuery.sortDir) {
            adminUsersQuery.q = keyword;
            adminUsersQuery.sortBy = sortBy;
            adminUsersQuery.sortDir = sortDir;
            adminUsersQuery.page = 0;
        }

        const params = new URLSearchParams({
            q: adminUsersQuery.q,
            page: String(adminUsersQuery.page),
            size: String(adminUsersQuery.size),
            sortBy: adminUsersQuery.sortBy,
            sortDir: adminUsersQuery.sortDir
        });

        const paged = await adminApiRequest(`/admin/users/search?${params.toString()}`, { method: 'GET' });
        adminUsersCache = Array.isArray(paged?.items) ? paged.items : [];
        adminUsersQuery.totalPages = Number(paged?.totalPages || 1) || 1;

        const pageInfo = document.getElementById('adminUserPageInfo');
        if (pageInfo) {
            const currentPageDisplay = Number(paged?.page || 0) + 1;
            pageInfo.textContent = `Trang ${currentPageDisplay}/${adminUsersQuery.totalPages}`;
        }

        const currentUser = getDashboardUser();

        if (!adminUsersCache.length) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-3">Không có user phù hợp.</td></tr>';
            return;
        }

        tbody.innerHTML = adminUsersCache.map(user => `
            <tr>
                <td><strong>${user.fullName || 'N/A'}</strong></td>
                <td>${user.email}</td>
                <td>
                    <select class="form-select form-select-sm" id="adminUserRole-${user.userId}" onchange="toggleUserBranchInput(${user.userId})">
                        <option value="ROLE_USER" ${user.role === 'ROLE_USER' ? 'selected' : ''}>ROLE_USER</option>
                        <option value="ROLE_BRANCH_ADMIN" ${user.role === 'ROLE_BRANCH_ADMIN' ? 'selected' : ''}>ROLE_BRANCH_ADMIN</option>
                        <option value="ROLE_ADMIN" ${user.role === 'ROLE_ADMIN' ? 'selected' : ''}>ROLE_ADMIN</option>
                    </select>
                </td>
                <td><input class="form-control form-control-sm" id="adminUserBranch-${user.userId}" value="${user.branchCode || ''}" placeholder="Chi nhánh"></td>
                <td>
                    <span class="badge ${user.active ? 'bg-success' : 'bg-secondary'}">${user.active ? 'ACTIVE' : 'DISABLED'}</span>
                </td>
                <td>${formatCurrency(user.walletBalance)}</td>
                <td>${user.createdAt ? new Date(user.createdAt).toLocaleString('vi-VN') : 'N/A'}</td>
                <td class="text-end">
                    <div class="btn-group btn-group-sm" role="group">
                        <button class="btn btn-primary" onclick="saveUserAccess(${user.userId})" ${currentUser && currentUser.userId === user.userId ? 'disabled title="Không tự đổi quyền"' : ''}>Lưu</button>
                        <button class="btn ${user.active ? 'btn-outline-danger' : 'btn-outline-success'}" onclick="toggleUserActive(${user.userId}, ${user.active ? 'false' : 'true'})" ${currentUser && currentUser.userId === user.userId ? 'disabled title="Không tự khóa"' : ''} ${user.role === 'ROLE_ADMIN' ? 'disabled title="Không khóa Admin tổng"' : ''}>
                            ${user.active ? 'Khóa' : 'Mở'}
                        </button>
                    </div>
                </td>
            </tr>
        `).join('');

        adminUsersCache.forEach(user => toggleUserBranchInput(user.userId));
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center text-danger py-3">${error.message}</td></tr>`;
    }
}

function adminUsersApplySearch() {
    adminUsersQuery.page = 0;
    loadAdminUsers();
}

function adminUsersPrevPage() {
    if (adminUsersQuery.page <= 0) {
        return;
    }
    adminUsersQuery.page -= 1;
    loadAdminUsers();
}

function adminUsersNextPage() {
    if (adminUsersQuery.page + 1 >= adminUsersQuery.totalPages) {
        return;
    }
    adminUsersQuery.page += 1;
    loadAdminUsers();
}

async function toggleUserActive(userId, active) {
    const target = adminUsersCache.find(item => item.userId === userId);
    if (!target) return;

    const label = active ? 'mở' : 'khóa';
    if (!confirm(`Bạn có chắc muốn ${label} tài khoản ${target.email}?`)) {
        return;
    }

    try {
        await adminApiRequest(`/admin/users/${userId}/status`, {
            method: 'PUT',
            body: JSON.stringify({ active })
        });
        showToast('success', `Đã ${label} tài khoản ${target.email}.`);
        await loadAdminUsers();
        await loadAdminSummary();
    } catch (error) {
        showToast('danger', error.message);
    }
}

function toggleUserBranchInput(userId) {
    const roleSelect = document.getElementById(`adminUserRole-${userId}`);
    const branchInput = document.getElementById(`adminUserBranch-${userId}`);
    if (!roleSelect || !branchInput) return;

    const branchRequired = roleSelect.value === 'ROLE_BRANCH_ADMIN';
    branchInput.disabled = roleSelect.value === 'ROLE_ADMIN';
    branchInput.placeholder = branchRequired ? 'Bắt buộc nhập mã chi nhánh' : 'Có thể để trống';
    if (roleSelect.value === 'ROLE_ADMIN') {
        branchInput.value = '';
    }
}

async function saveUserAccess(userId) {
    const role = document.getElementById(`adminUserRole-${userId}`)?.value;
    const branchCode = document.getElementById(`adminUserBranch-${userId}`)?.value.trim().toUpperCase() || '';

    try {
        await adminApiRequest(`/admin/users/${userId}`, {
            method: 'PUT',
            body: JSON.stringify({ role, branchCode })
        });
        showToast('success', 'Cập nhật phân quyền thành công.');
        await loadAdminUsers();
        await loadAdminSummary();
    } catch (error) {
        showToast('danger', error.message);
    }
}

async function loadAdminAuditLogs() {
    const container = document.getElementById('adminAuditList');
    if (!container) return;

    container.innerHTML = '<div class="text-muted">Đang tải audit logs...</div>';
    try {
        const logs = await adminApiRequest('/audit-logs', { method: 'GET' });
        if (!logs.length) {
            container.innerHTML = '<div class="text-muted">Chưa có audit log nào.</div>';
            return;
        }

        container.innerHTML = logs.map(log => `
            <div class="border rounded p-2 mb-2 bg-white">
                <div class="d-flex justify-content-between flex-wrap gap-2">
                    <div>
                        <div class="fw-semibold">${log.action || 'ACTION'} • ${log.result || 'N/A'}</div>
                        <div class="small text-muted">${log.actorEmail || 'anonymous'} • ${log.httpMethod || 'N/A'} ${log.requestPath || ''}</div>
                        <div class="small">${log.target || ''}</div>
                        <div class="small text-muted">${log.details || ''}</div>
                    </div>
                    <div class="small text-muted">${log.createdAt ? new Date(log.createdAt).toLocaleString('vi-VN') : ''}</div>
                </div>
            </div>
        `).join('');
    } catch (error) {
        container.innerHTML = `<div class="text-danger">${error.message}</div>`;
    }
}