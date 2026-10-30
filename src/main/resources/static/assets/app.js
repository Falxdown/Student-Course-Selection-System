(() => {
    'use strict';

    const AUTH_KEY = 'ccs.auth.v1';
    const state = {
        accessToken: null,
        refreshToken: null,
        user: null,
        courses: [],
        allOrders: [],
        orderFilter: '',
        courseTotal: 0,
        view: 'courses',
        refreshing: null
    };

    class ApiError extends Error {
        constructor(message, code, status) {
            super(message);
            this.code = code;
            this.status = status;
        }
    }

    const $ = (selector) => document.querySelector(selector);
    const $$ = (selector) => Array.from(document.querySelectorAll(selector));

    function escapeHtml(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#39;');
    }

    function toast(message, type = 'info') {
        const node = document.createElement('div');
        node.className = `toast ${type}`;
        node.textContent = message;
        $('#toastStack').appendChild(node);
        setTimeout(() => {
            node.style.opacity = '0';
            node.style.transform = 'translateY(8px)';
            setTimeout(() => node.remove(), 250);
        }, 4200);
    }

    function persistAuth() {
        localStorage.setItem(AUTH_KEY, JSON.stringify({
            accessToken: state.accessToken,
            refreshToken: state.refreshToken,
            user: state.user
        }));
    }

    function clearAuth() {
        state.accessToken = null;
        state.refreshToken = null;
        state.user = null;
        state.courses = [];
        state.allOrders = [];
        localStorage.removeItem(AUTH_KEY);
    }

    async function parseResponse(response) {
        let payload = null;
        try {
            payload = await response.json();
        } catch (ignored) {
            // 204 或非 JSON 错误页
        }
        if (!response.ok || (payload && payload.code !== 0)) {
            const message = payload?.message || `请求失败（HTTP ${response.status}）`;
            throw new ApiError(message, payload?.code, response.status);
        }
        return payload?.data;
    }

    async function refreshAccessToken() {
        if (!state.refreshToken) {
            throw new ApiError('登录已失效，请重新登录', 40101, 401);
        }
        if (state.refreshing) {
            return state.refreshing;
        }
        state.refreshing = (async () => {
            const response = await fetch('/api/v1/auth/refresh', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({refreshToken: state.refreshToken})
            });
            const data = await parseResponse(response);
            state.accessToken = data.accessToken;
            state.refreshToken = data.refreshToken;
            state.user = data.student;
            persistAuth();
        })();
        try {
            return await state.refreshing;
        } finally {
            state.refreshing = null;
        }
    }

    async function api(path, options = {}, retry = true) {
        const headers = {...(options.headers || {})};
        if (state.accessToken) {
            headers.Authorization = `Bearer ${state.accessToken}`;
        }
        if (options.body && !headers['Content-Type']) {
            headers['Content-Type'] = 'application/json';
        }
        const response = await fetch(path, {...options, headers});
        if (response.status === 401 && retry && !path.startsWith('/api/v1/auth/')) {
            try {
                await refreshAccessToken();
                return api(path, options, false);
            } catch (error) {
                clearAuth();
                showLogin();
                throw error;
            }
        }
        return parseResponse(response);
    }

    async function login(event) {
        event.preventDefault();
        const button = $('#loginBtn');
        const errorBox = $('#loginError');
        errorBox.hidden = true;
        button.disabled = true;
        button.textContent = '正在验证…';
        try {
            const data = await api('/api/v1/auth/login', {
                method: 'POST',
                body: JSON.stringify({
                    studentNo: $('#studentNo').value.trim(),
                    password: $('#password').value
                })
            }, false);
            state.accessToken = data.accessToken;
            state.refreshToken = data.refreshToken;
            state.user = data.student;
            persistAuth();
            await enterWorkspace();
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.hidden = false;
        } finally {
            button.disabled = false;
            button.innerHTML = '进入选课大厅 <span>→</span>';
        }
    }

    async function enterWorkspace() {
        $('#loginView').hidden = true;
        $('#topbar').hidden = false;
        $('#workspace').hidden = false;
        $('#userName').textContent = state.user.name;
        $('#userRole').textContent = state.user.role === 'ADMIN' ? '管理员' : state.user.studentNo;
        $$('.admin-only').forEach(node => node.hidden = state.user.role !== 'ADMIN');
        setDefaultCourseTimes();
        switchView('courses');
        await Promise.allSettled([loadOrders(''), loadAdminStats()]);
    }

    function showLogin() {
        $('#loginView').hidden = false;
        $('#topbar').hidden = true;
        $('#workspace').hidden = true;
        if ($('#loginError')) {
            $('#loginError').hidden = true;
        }
    }

    function logout() {
        clearAuth();
        showLogin();
        toast('已退出登录');
    }

    function fillLogin(studentNo, password) {
        $('#studentNo').value = studentNo;
        $('#password').value = password;
        $('#studentNo').focus();
    }

    function switchView(view) {
        state.view = view;
        $('#coursesView').hidden = view !== 'courses';
        $('#ordersView').hidden = view !== 'orders';
        $('#adminView').hidden = view !== 'admin';
        $$('.nav-btn').forEach(button => {
            button.classList.toggle('active', button.dataset.nav === view);
        });
        const headings = {
            courses: ['COURSE MARKET', '选课大厅', '名额实时同步，选中后可在「我的课表」查看订单。'],
            orders: ['MY SCHEDULE', '我的课表', '这里保存你的每一笔选课订单与取消记录。'],
            admin: ['OPERATIONS', '管理台', '课程发布、容量管理与选课运营数据。']
        };
        const [eyebrow, title, subtitle] = headings[view] || headings.courses;
        $('#pageEyebrow').textContent = eyebrow;
        $('#pageTitle').textContent = title;
        $('#pageSubtitle').textContent = subtitle;
        if (view === 'courses') {
            loadCourses();
        } else if (view === 'orders') {
            loadOrders(state.orderFilter);
        } else if (view === 'admin') {
            loadAdminStats();
        }
    }

    async function loadCourses() {
        const grid = $('#courseGrid');
        grid.innerHTML = '<div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div>';
        const params = new URLSearchParams({
            page: '1',
            size: '60',
            sort: $('#sort').value || 'hot'
        });
        const keyword = $('#keyword').value.trim();
        const category = $('#category').value;
        if (keyword) params.set('keyword', keyword);
        if (category) params.set('category', category);
        try {
            const data = await api(`/api/v1/courses?${params.toString()}`);
            state.courses = data.records || [];
            state.courseTotal = data.total || 0;
            renderCourses();
            updateSummary();
        } catch (error) {
            grid.innerHTML = '';
            toast(error.message, 'error');
        }
    }

    function hasConfirmedCourse(courseId) {
        return state.allOrders.some(order => order.status === 'CONFIRMED' && order.courseId === courseId);
    }

    function renderCourses() {
        const grid = $('#courseGrid');
        const empty = $('#courseEmpty');
        if (!state.courses.length) {
            grid.innerHTML = '';
            empty.hidden = false;
            return;
        }
        empty.hidden = true;
        grid.innerHTML = state.courses.map((course, index) => {
            const ratio = Math.max(0, Math.min(100, course.capacity ? course.selectedCount / course.capacity * 100 : 0));
            const remaining = Number(course.remainingStock ?? 0);
            const selected = hasConfirmedCourse(course.id);
            let buttonText = '立即抢选';
            let disabled = false;
            let extraClass = '';
            if (selected) {
                buttonText = '已在我的课表';
                disabled = true;
                extraClass = 'closed';
            } else if (!course.selectionOpen) {
                buttonText = '不在选课时间';
                disabled = true;
                extraClass = 'closed';
            } else if (remaining <= 0) {
                buttonText = '名额已满';
                disabled = true;
                extraClass = 'closed';
            }
            const stockClass = remaining <= 0 ? 'done' : (remaining <= 5 ? 'low' : '');
            const barClass = ratio >= 100 ? 'full' : (ratio >= 75 ? 'warn' : '');
            return `
                <article class="course-card" data-index="${String(index + 1).padStart(2, '0')}">
                    <div class="course-top">
                        <span class="course-code">${escapeHtml(course.courseCode)}</span>
                        <span class="category-pill">${escapeHtml(course.category)}</span>
                    </div>
                    <h3>${escapeHtml(course.name)}</h3>
                    <div class="course-teacher">${escapeHtml(course.teacher)} · ${course.credits} 学分</div>
                    <p class="course-desc">${escapeHtml(course.description || '暂无课程简介')}</p>
                    <div class="stock-row">
                        <span>剩余名额</span>
                        <strong class="${stockClass}">${remaining}<small> / ${course.capacity}</small></strong>
                    </div>
                    <div class="progress"><i class="${barClass}" style="width:${Math.min(100, ratio)}%"></i></div>
                    <div class="course-meta">
                        <span>已选 <strong>${course.selectedCount}</strong> 人</span>
                        <span>${formatDate(course.selectEndTime, true)} 截止</span>
                    </div>
                    <button class="course-action ${extraClass}" ${disabled ? 'disabled' : ''}
                            onclick="App.grab(${course.id}, this)">${buttonText}</button>
                </article>
            `;
        }).join('');
        updateSummary();
    }

    function updateSummary() {
        $('#courseCount').textContent = state.courseTotal || state.courses.length;
        $('#orderCount').textContent = state.allOrders.filter(order => order.status === 'CONFIRMED').length;
    }

    async function grab(courseId, button) {
        const original = button.textContent;
        button.disabled = true;
        button.textContent = '锁定名额中…';
        const key = (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`)
            .replaceAll('-', '');
        try {
            const task = await api('/api/v1/selections', {
                method: 'POST',
                headers: {'Idempotency-Key': key},
                body: JSON.stringify({courseId})
            });
            toast(`请求已受理，正在排队落单（${task.requestId.slice(0, 8)}…）`);
            await pollTask(task.requestId);
        } catch (error) {
            toast(error.message, 'error');
            button.disabled = false;
            button.textContent = original;
            await loadCourses();
        }
    }

    async function pollTask(requestId) {
        for (let attempt = 0; attempt < 20; attempt++) {
            await new Promise(resolve => setTimeout(resolve, 350));
            try {
                const task = await api(`/api/v1/selections/tasks/${encodeURIComponent(requestId)}`);
                if (task.status === 'SUCCESS') {
                    toast(`选课成功，订单号 ${task.orderNo}`, 'success');
                    await Promise.allSettled([loadCourses(), loadOrders('')]);
                    return;
                }
                if (task.status === 'FAILED') {
                    toast(`选课失败：${task.failReason || '名额不足'}`, 'error');
                    await Promise.allSettled([loadCourses(), loadOrders('')]);
                    return;
                }
            } catch (error) {
                toast(error.message, 'error');
                return;
            }
        }
        toast('请求仍在处理中，可稍后在「我的课表」查看结果');
        await Promise.allSettled([loadCourses(), loadOrders('')]);
    }

    async function loadOrders(filter = '') {
        state.orderFilter = filter;
        try {
            const data = await api('/api/v1/selections/orders?page=1&size=100');
            state.allOrders = data.records || [];
            renderOrders();
            updateSummary();
            if (state.courses.length) renderCourses();
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    function renderOrders() {
        const list = $('#orderList');
        const empty = $('#orderEmpty');
        const filter = state.orderFilter;
        const records = state.allOrders.filter(order => !filter || order.status === filter);
        const buttons = $$('#orderFilter button');
        const filters = ['', 'CONFIRMED', 'CANCELLED'];
        buttons.forEach((button, index) => button.classList.toggle('active', filters[index] === filter));
        if (!records.length) {
            list.innerHTML = '';
            empty.hidden = false;
            return;
        }
        empty.hidden = true;
        list.innerHTML = records.map(order => `
            <div class="order-item">
                <div class="order-main">
                    <strong>${escapeHtml(order.courseName)}</strong>
                    <span>${escapeHtml(order.courseCode)} · 订单号 ${escapeHtml(order.orderNo)}</span>
                    <span>${formatDate(order.createdAt)}${order.cancelledAt ? ` · 取消于 ${formatDate(order.cancelledAt)}` : ''}</span>
                </div>
                <div class="order-side">
                    <span class="status-pill ${order.status === 'CONFIRMED' ? 'confirmed' : 'cancelled'}">
                        ${order.status === 'CONFIRMED' ? '已选中' : '已取消'}
                    </span>
                    ${order.status === 'CONFIRMED' ? `<button class="cancel-btn" onclick="App.cancelOrder('${escapeHtml(order.orderNo)}')">取消选课</button>` : ''}
                </div>
            </div>
        `).join('');
    }

    async function cancelOrder(orderNo) {
        const confirmed = await confirmModal('取消这门课？', '取消后名额会立即归还，可在选课时间内重新选择。');
        if (!confirmed) return;
        try {
            await api(`/api/v1/selections/orders/${encodeURIComponent(orderNo)}`, {method: 'DELETE'});
            toast('已取消选课，名额已归还', 'success');
            await Promise.allSettled([loadOrders(state.orderFilter), loadCourses()]);
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    async function loadAdminStats() {
        if (!state.user || state.user.role !== 'ADMIN') return;
        try {
            const data = await api('/api/v1/admin/selections/statistics');
            $('#adminStats').innerHTML = `
                ${statCard('已发布课程', data.publishedCourses)}
                ${statCard('今日订单', data.todayOrders)}
                ${statCard('有效订单', data.confirmedOrders)}
                ${statCard('任务成功率', `${data.taskSuccessRate}<small>%</small>`)}
                ${statCard('待处理任务', data.pendingTasks)}
            `;
        } catch (error) {
            $('#adminStats').innerHTML = `<div class="empty">${escapeHtml(error.message)}</div>`;
        }
    }

    function statCard(label, value) {
        return `<div class="stat-card"><span>${label}</span><strong>${value}</strong></div>`;
    }

    async function createCourse(event) {
        event.preventDefault();
        const form = event.currentTarget;
        const values = new FormData(form);
        const start = values.get('selectStartTime');
        const end = values.get('selectEndTime');
        if (!start || !end || new Date(end) <= new Date(start)) {
            toast('选课结束时间必须晚于开始时间', 'error');
            return;
        }
        const body = {
            courseCode: values.get('courseCode').trim(),
            name: values.get('name').trim(),
            teacher: values.get('teacher').trim(),
            category: values.get('category').trim(),
            credits: Number(values.get('credits')),
            capacity: Number(values.get('capacity')),
            selectStartTime: `${start}:00`,
            selectEndTime: `${end}:00`,
            status: values.get('status'),
            description: values.get('description')?.trim() || null
        };
        const button = form.querySelector('button[type="submit"]');
        button.disabled = true;
        button.textContent = '创建中…';
        try {
            await api('/api/v1/admin/courses', {method: 'POST', body: JSON.stringify(body)});
            toast('课程创建成功', 'success');
            form.reset();
            setDefaultCourseTimes();
            await Promise.allSettled([loadAdminStats(), loadCourses()]);
        } catch (error) {
            toast(error.message, 'error');
        } finally {
            button.disabled = false;
            button.textContent = '创建课程';
        }
    }

    function setDefaultCourseTimes() {
        const form = $('#courseForm');
        if (!form) return;
        const start = form.querySelector('[name="selectStartTime"]');
        const end = form.querySelector('[name="selectEndTime"]');
        if (!start || !end) return;
        const now = new Date();
        const later = new Date(now.getTime() + 30 * 24 * 3600 * 1000);
        start.value = toLocalInput(now);
        end.value = toLocalInput(later);
    }

    function toLocalInput(date) {
        const pad = value => String(value).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function formatDate(value, short = false) {
        if (!value) return '—';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return String(value);
        const options = short
            ? {month: '2-digit', day: '2-digit'}
            : {year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'};
        return date.toLocaleString('zh-CN', options).replaceAll('/', '-');
    }

    function refreshCurrent() {
        if (state.view === 'courses') loadCourses();
        else if (state.view === 'orders') loadOrders(state.orderFilter);
        else loadAdminStats();
    }

    function goHome() {
        switchView('courses');
    }

    let modalResolver = null;

    function confirmModal(title, text) {
        $('#modalTitle').textContent = title;
        $('#modalText').textContent = text;
        $('#confirmModal').hidden = false;
        return new Promise(resolve => {
            modalResolver = resolve;
        });
    }

    function closeModal(result = false) {
        $('#confirmModal').hidden = true;
        if (modalResolver) {
            modalResolver(result);
            modalResolver = null;
        }
    }

    async function init() {
        $('#loginForm').addEventListener('submit', login);
        $('#courseForm').addEventListener('submit', createCourse);
        $('#modalConfirm').addEventListener('click', () => closeModal(true));
        $('#confirmModal').addEventListener('click', event => {
            if (event.target.id === 'confirmModal') closeModal(false);
        });
        setDefaultCourseTimes();

        const stored = localStorage.getItem(AUTH_KEY);
        if (!stored) {
            showLogin();
            return;
        }
        try {
            const parsed = JSON.parse(stored);
            state.accessToken = parsed.accessToken;
            state.refreshToken = parsed.refreshToken;
            state.user = parsed.user;
            state.user = await api('/api/v1/auth/me');
            persistAuth();
            await enterWorkspace();
        } catch (error) {
            clearAuth();
            showLogin();
        }
    }

    window.App = {
        fillLogin,
        logout,
        switchView,
        goHome,
        loadCourses,
        loadOrders,
        grab,
        cancelOrder,
        loadAdminStats,
        refreshCurrent,
        closeModal
    };

    document.addEventListener('DOMContentLoaded', init);
})();
