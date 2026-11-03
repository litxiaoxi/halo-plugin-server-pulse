"use strict";

(function () {
    const body = document.body;
    const API_BASE = resolveApiBase();
    const API_URL = API_BASE + "/metrics";
    const THEME_KEY = "server-pulse-theme";
    const DEFAULT_REFRESH_SECONDS = 3;
    const MIN_REFRESH_SECONDS = 1;
    const MAX_REFRESH_SECONDS = 30;
    const METRIC_TWEEN_MS = 720;

    const elements = {
        dashboard: document.getElementById("dashboard"),
        locked: document.getElementById("locked-state"),
        error: document.getElementById("error-state"),
        errorMessage: document.getElementById("error-message"),
        retry: document.getElementById("retry-button"),
        livePill: document.getElementById("live-pill"),
        liveText: document.getElementById("live-text"),
        serverName: document.getElementById("server-name"),
        heroSub: document.getElementById("hero-sub"),
        uptime: document.getElementById("uptime"),
        lastUpdated: document.getElementById("last-updated"),
        refreshLabel: document.getElementById("refresh-label"),
        degraded: document.getElementById("degraded-note"),
        historyPanel: document.getElementById("history-panel"),
        detailsPanel: document.getElementById("details-panel"),
        chart: document.getElementById("history-chart"),
        chartEmpty: document.getElementById("chart-empty"),
        accessBadge: document.getElementById("access-badge"),
        footerVersion: document.getElementById("footer-version"),
        themeToggle: document.getElementById("theme-toggle")
    };

    const state = {
        timer: null,
        inFlight: false,
        hasLoaded: false,
        consecutiveErrors: 0,
        refreshSeconds: DEFAULT_REFRESH_SECONDS,
        history: [],
        theme: null
    };

    // 每个指标缓存自身节点与当前显示的数值，检测到新数据时从当前值补间到目标值。
    const metrics = {};

    function resolveApiBase() {
        const configured = (body.dataset.apiBase || "").trim();
        if (configured) {
            return configured.replace(/\/+$/, "");
        }
        const path = window.location.pathname.replace(/\/pulse\/?$/, "");
        return path + "/apis/api.serverpulse.halo.run/v1alpha1";
    }

    function init() {
        initTheme();
        bindEvents();
        observeChart();
        fetchMetrics();
    }

    function bindEvents() {
        elements.retry.addEventListener("click", function () {
            state.consecutiveErrors = 0;
            fetchMetrics();
        });
        elements.themeToggle.addEventListener("click", toggleTheme);
        document.addEventListener("visibilitychange", function () {
            if (document.hidden) {
                clearTimer();
            } else {
                fetchMetrics();
            }
        });
        window.addEventListener("focus", function () {
            if (!document.hidden) {
                fetchMetrics();
            }
        });
        if (window.matchMedia) {
            const media = window.matchMedia("(prefers-color-scheme: dark)");
            const onChange = function () {
                if (state.theme === "auto") {
                    drawHistory();
                }
            };
            if (media.addEventListener) {
                media.addEventListener("change", onChange);
            } else if (media.addListener) {
                media.addListener(onChange);
            }
        }
        window.addEventListener("resize", drawHistory);
    }

    function initTheme() {
        let stored = null;
        try {
            stored = localStorage.getItem(THEME_KEY);
        } catch (ignored) {
            stored = null;
        }
        if (stored === "light" || stored === "dark" || stored === "auto") {
            applyTheme(stored);
        } else {
            applyTheme(document.documentElement.dataset.theme || "auto");
        }
        updateThemeButton();
    }

    function toggleTheme() {
        // Flip the appearance the user actually sees, so a single click always works,
        // even when the current theme is "auto" and follows the system color scheme.
        const next = effectiveAppearance() === "dark" ? "light" : "dark";
        applyTheme(next);
        try {
            localStorage.setItem(THEME_KEY, next);
        } catch (ignored) {
            // Theme still applies to this page even when storage is unavailable.
        }
        updateThemeButton();
        drawHistory();
    }

    function effectiveAppearance() {
        if (state.theme === "light" || state.theme === "dark") {
            return state.theme;
        }
        return systemPrefersDark() ? "dark" : "light";
    }

    function systemPrefersDark() {
        return !!(window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches);
    }

    function applyTheme(theme) {
        state.theme = theme;
        document.documentElement.dataset.theme = theme;
    }

    function updateThemeButton() {
        const labels = {
            auto: "主题：跟随系统",
            light: "主题：亮色",
            dark: "主题：暗色"
        };
        const label = labels[state.theme] || labels.auto;
        elements.themeToggle.setAttribute("aria-label", label);
        elements.themeToggle.setAttribute("title", label);
    }

    function fetchMetrics() {
        if (state.inFlight || document.hidden) {
            return;
        }
        clearTimer();
        state.inFlight = true;
        const controller = new AbortController();
        const timeout = window.setTimeout(function () {
            controller.abort();
        }, 5000);

        fetch(API_URL, {
            method: "GET",
            credentials: "same-origin",
            cache: "no-store",
            headers: { Accept: "application/json" },
            signal: controller.signal
        })
            .then(function (response) {
                return response.json().catch(function () {
                    return {};
                }).then(function (payload) {
                    return { response: response, payload: payload };
                });
            })
            .then(function (result) {
                window.clearTimeout(timeout);
                handleResponse(result.response, result.payload);
            })
            .catch(function (error) {
                window.clearTimeout(timeout);
                handleFailure(error);
            })
            .finally(function () {
                state.inFlight = false;
            });
    }

    function handleResponse(response, payload) {
        if (response.ok) {
            state.consecutiveErrors = 0;
            state.hasLoaded = true;
            renderDashboard(payload);
            if (payload && payload.stale) {
                setLive("数据已过期", "is-paused");
            } else {
                setLive("实时更新", "");
            }
            scheduleNext(refreshMillis());
            return;
        }

        if (response.status === 401) {
            state.hasLoaded = false;
            showLocked();
            setLive("等待登录", "is-paused");
            scheduleNext(30000);
            return;
        }

        if (response.status === 403) {
            state.hasLoaded = false;
            showError("当前账号没有查看服务器监控的权限，请联系站点管理员。");
            setLive("没有权限", "is-error");
            scheduleNext(30000);
            return;
        }

        if (response.status === 503) {
            setLive("正在初始化", "is-paused");
            if (!state.hasLoaded) {
                elements.dashboard.hidden = false;
                elements.dashboard.setAttribute("aria-busy", "true");
            }
            scheduleNext(2000);
            return;
        }

        handleFailure(new Error(payload && payload.message ? payload.message : "HTTP " + response.status));
    }

    function handleFailure(error) {
        state.consecutiveErrors += 1;
        const message = error && error.name === "AbortError"
            ? "请求超时，请检查网络或服务状态。"
            : "无法连接监控接口，请稍后重试。";

        if (state.hasLoaded) {
            setLive("连接中断，重试中", "is-error");
        } else {
            showError(message);
            setLive("连接失败", "is-error");
        }

        const backoff = Math.min(30000, 3000 * Math.pow(2, Math.max(0, state.consecutiveErrors - 1)));
        scheduleNext(backoff);
    }

    function scheduleNext(delay) {
        clearTimer();
        if (document.hidden) {
            return;
        }
        state.timer = window.setTimeout(fetchMetrics, Math.max(1000, delay));
    }

    function clearTimer() {
        if (state.timer !== null) {
            window.clearTimeout(state.timer);
            state.timer = null;
        }
    }

    function refreshMillis() {
        return Math.max(MIN_REFRESH_SECONDS, Math.min(MAX_REFRESH_SECONDS, state.refreshSeconds)) * 1000;
    }

    function setLive(text, className) {
        elements.liveText.textContent = text;
        elements.livePill.classList.remove("is-error", "is-paused");
        if (className) {
            elements.livePill.classList.add(className);
        }
    }

    function showLocked() {
        elements.dashboard.hidden = true;
        elements.error.hidden = true;
        elements.locked.hidden = false;
        const redirect = encodeURIComponent(window.location.pathname + window.location.search);
        const basePath = window.location.pathname.replace(/\/pulse\/?$/, "");
        document.getElementById("login-link").href = basePath + "/login?redirect=" + redirect;
    }

    function showError(message) {
        elements.dashboard.hidden = true;
        elements.locked.hidden = true;
        elements.error.hidden = false;
        elements.errorMessage.textContent = message;
    }

    function showDashboard() {
        elements.locked.hidden = true;
        elements.error.hidden = true;
        elements.dashboard.hidden = false;
        elements.dashboard.setAttribute("aria-busy", "false");
    }

    function renderDashboard(payload) {
        showDashboard();
        state.refreshSeconds = clampRefresh(payload && payload.ui ? payload.ui.refreshInterval : DEFAULT_REFRESH_SECONDS);

        const serverName = (payload && payload.serverName ? payload.serverName : "").trim()
            || (body.dataset.siteTitle || "").trim()
            || "服务器监控";
        elements.serverName.textContent = serverName;
        document.title = "ServerPulse · " + serverName;
        elements.footerVersion.textContent = payload.pluginVersion || body.dataset.pluginVersion || "";

        const info = payload.info || null;
        const usage = payload.usage || {};
        const runtime = payload.runtime || null;
        const ui = payload.ui || {};
        const access = payload.access || {};

        renderHero(payload, info, usage);
        renderMetric("cpu", usage.cpuPercent, cpuDetail(info, usage), cpuSub(info));
        renderMetric("memory", usage.memoryPercent, bytePair(usage.memoryUsedBytes, usage.memoryTotalBytes), memorySub(info, usage));
        renderMetric("storage", usage.storagePercent, bytePair(usage.storageUsedBytes, usage.storageTotalBytes), storageSub(info));

        elements.uptime.textContent = formatDuration(usage.uptimeSeconds);
        elements.lastUpdated.textContent = payload.stale ? "数据已过期" : formatTime(payload.generatedAt);
        elements.refreshLabel.textContent = "每 " + state.refreshSeconds + " 秒";
        elements.degraded.hidden = !payload.degraded;

        const showDetails = Boolean(ui.showDetails) && Boolean(info);
        elements.detailsPanel.hidden = !showDetails;
        if (showDetails) {
            renderDetails(info, runtime);
        }

        const showHistory = Boolean(ui.showHistory);
        elements.historyPanel.hidden = !showHistory || !Array.isArray(payload.history) || payload.history.length === 0;
        state.history = showHistory && Array.isArray(payload.history) ? payload.history : [];
        drawHistory();

        elements.accessBadge.textContent = accessBadgeText(access);
    }

    function accessBadgeText(access) {
        if (access.mode === "public") {
            return "公开访问";
        }
        if (access.mode === "users") {
            return access.authenticated ? "已登录 · 指定用户可见" : "指定用户";
        }
        return access.authenticated ? "已登录 · 仅登录可见" : "仅登录用户";
    }

    function renderHero(payload, info, usage) {
        if (info) {
            const parts = [];
            if (info.os && info.os.family) {
                parts.push(info.os.family);
            }
            if (info.processor && info.processor.cores) {
                parts.push(info.processor.cores + " 核");
            }
            if (usage.memoryTotalBytes) {
                parts.push(formatBytes(usage.memoryTotalBytes) + " 内存");
            }
            elements.heroSub.textContent = parts.join(" · ") || "服务器运行状态";
        } else {
            elements.heroSub.textContent = "服务器运行状态";
        }
        if (payload.stale) {
            elements.lastUpdated.textContent = "数据已过期";
        }
    }

    function renderMetric(prefix, percent, detail, sub) {
        const metric = metricParts(prefix);
        tweenMetric(metric, clampPercent(percent));

        if (metric.detail) {
            metric.detail.textContent = detail || "--";
        }
        if (metric.sub) {
            metric.sub.textContent = sub || "--";
        }
    }

    function metricParts(prefix) {
        let metric = metrics[prefix];
        if (!metric) {
            metric = {
                gauge: document.getElementById(prefix + "-gauge"),
                value: document.getElementById(prefix + "-value"),
                bar: document.getElementById(prefix + "-bar"),
                detail: document.getElementById(prefix + "-detail"),
                sub: document.getElementById(prefix + "-sub"),
                current: 0,
                frame: 0
            };
            metrics[prefix] = metric;
        }
        return metric;
    }

    function tweenMetric(metric, target) {
        if (!metric.gauge || !metric.value || !metric.bar) {
            return;
        }
        metric.gauge.setAttribute("aria-valuenow", String(Math.round(target)));

        // 新数据可能在上一段动画中途到达，从当前显示值接着补间，避免跳变。
        if (metric.frame) {
            window.cancelAnimationFrame(metric.frame);
            metric.frame = 0;
        }

        const from = metric.current;
        const distance = target - from;
        if (prefersReducedMotion() || Math.abs(distance) < 0.05) {
            paintMetric(metric, target);
            return;
        }

        const startedAt = performance.now();
        const tick = function (now) {
            const progress = Math.min(1, (now - startedAt) / METRIC_TWEEN_MS);
            if (progress >= 1) {
                metric.frame = 0;
                paintMetric(metric, target);
                return;
            }
            // 先快后慢，数值快速接近目标后柔和收敛。
            paintMetric(metric, from + distance * (1 - Math.pow(1 - progress, 5)));
            metric.frame = window.requestAnimationFrame(tick);
        };
        metric.frame = window.requestAnimationFrame(tick);
    }

    function paintMetric(metric, value) {
        metric.current = value;
        metric.gauge.style.setProperty("--value", value.toFixed(2));
        metric.value.textContent = formatPercent(value);
        metric.bar.style.width = value.toFixed(2) + "%";
    }

    function prefersReducedMotion() {
        return !!(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);
    }

    function renderDetails(info, runtime) {
        const processor = info.processor || {};
        const os = info.os || {};
        const memory = info.memory || {};
        const storage = info.storage || {};

        setText("detail-cpu", processor.name || "N/A");
        setText("detail-cpu-sub", joinNonEmpty([
            processor.cores ? processor.cores + " 核" : "",
            formatHz(processor.clockHz),
            processor.bitDepth ? processor.bitDepth + "-bit" : "",
            processor.architecture || ""
        ]));

        setText("detail-os", joinNonEmpty([os.family || "", os.version || ""], " "));
        setText("detail-os-sub", joinNonEmpty([
            os.codeName || "",
            os.bitness ? os.bitness + "-bit" : ""
        ]));

        setText("detail-memory", formatBytes(memory.totalBytes));
        setText("detail-memory-sub", memory.type || "物理内存");

        setText("detail-storage", formatBytes(storage.totalBytes));
        setText("detail-storage-sub", joinNonEmpty([
            storage.model || "",
            storage.diskCount ? storage.diskCount + " 块磁盘" : "",
            storage.swapTotalBytes ? "Swap " + formatBytes(storage.swapTotalBytes) : ""
        ]));

        setText("detail-process", info.processCount > 0 ? String(info.processCount) : "N/A");

        if (runtime) {
            setText("detail-runtime", formatDuration(runtime.jvmUptimeSeconds));
            setText("detail-runtime-sub", runtime.heapMaxBytes > 0
                ? formatBytes(runtime.heapUsedBytes) + " / " + formatBytes(runtime.heapMaxBytes)
                : formatBytes(runtime.heapUsedBytes));
        } else {
            setText("detail-runtime", "N/A");
            setText("detail-runtime-sub", "");
        }
    }

    function cpuDetail(info, usage) {
        if (info && info.processor && info.processor.name) {
            return info.processor.name;
        }
        return "CPU 使用率 " + formatPercent(usage.cpuPercent) + "%";
    }

    function cpuSub(info) {
        if (!info || !info.processor) {
            return "实时采样";
        }
        return joinNonEmpty([
            info.processor.cores ? info.processor.cores + " 核" : "",
            formatHz(info.processor.clockHz),
            info.processor.bitDepth ? info.processor.bitDepth + "-bit" : ""
        ]);
    }

    function memorySub(info, usage) {
        const type = info && info.memory ? info.memory.type : "";
        return joinNonEmpty([
            type || "",
            formatBytes(usage.memoryTotalBytes) + " 总量"
        ]);
    }

    function storageSub(info) {
        if (!info || !info.storage) {
            return "根分区";
        }
        const storage = info.storage;
        return joinNonEmpty([
            storage.mount || "",
            storage.diskCount ? storage.diskCount + " 块磁盘" : ""
        ]);
    }

    function setText(id, value) {
        const element = document.getElementById(id);
        if (element) {
            element.textContent = value == null ? "" : String(value);
        }
    }

    function drawHistory() {
        const canvas = elements.chart;
        if (!canvas || elements.historyPanel.hidden) {
            return;
        }
        const rect = canvas.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0) {
            return;
        }

        const dpr = window.devicePixelRatio || 1;
        canvas.width = Math.round(rect.width * dpr);
        canvas.height = Math.round(rect.height * dpr);
        const ctx = canvas.getContext("2d");
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, rect.width, rect.height);

        const points = state.history;
        elements.chartEmpty.hidden = points.length >= 2;
        if (points.length < 2) {
            return;
        }

        const style = getComputedStyle(document.documentElement);
        const colors = {
            cpu: style.getPropertyValue("--cpu").trim() || "#6d5dfc",
            memory: style.getPropertyValue("--memory").trim() || "#0ea5e9",
            storage: style.getPropertyValue("--storage").trim() || "#f59e0b",
            grid: style.getPropertyValue("--border").trim() || "rgba(0,0,0,.08)",
            text: style.getPropertyValue("--subtle").trim() || "#8a94a6"
        };

        const padding = { top: 10, right: 8, bottom: 22, left: 34 };
        const width = rect.width - padding.left - padding.right;
        const height = rect.height - padding.top - padding.bottom;

        ctx.font = "10px " + (style.getPropertyValue("--font-mono").trim() || "ui-monospace, monospace");
        ctx.lineWidth = 1;
        ctx.strokeStyle = colors.grid;
        ctx.fillStyle = colors.text;
        ctx.textAlign = "right";
        ctx.textBaseline = "middle";

        [100, 75, 50, 25, 0].forEach(function (value) {
            const y = padding.top + height * (1 - value / 100);
            ctx.beginPath();
            ctx.moveTo(padding.left, y);
            ctx.lineTo(padding.left + width, y);
            ctx.stroke();
            ctx.fillText(value + "%", padding.left - 8, y);
        });

        const xFor = function (index) {
            return padding.left + (width * index) / (points.length - 1);
        };
        const yFor = function (value) {
            return padding.top + height * (1 - clampPercent(value) / 100);
        };

        drawLine(ctx, points, "cpuPercent", xFor, yFor, colors.cpu);
        drawLine(ctx, points, "memoryPercent", xFor, yFor, colors.memory);
        drawLine(ctx, points, "storagePercent", xFor, yFor, colors.storage);

        ctx.textAlign = "left";
        ctx.textBaseline = "bottom";
        ctx.fillStyle = colors.text;
        ctx.fillText("2 分钟前", padding.left, rect.height - 2);
        ctx.textAlign = "right";
        ctx.fillText("现在", padding.left + width, rect.height - 2);
    }

    function drawLine(ctx, points, key, xFor, yFor, color) {
        ctx.beginPath();
        points.forEach(function (point, index) {
            const x = xFor(index);
            const y = yFor(point[key]);
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.strokeStyle = color;
        ctx.lineWidth = 2;
        ctx.lineJoin = "round";
        ctx.lineCap = "round";
        ctx.stroke();

        if (key === "cpuPercent") {
            ctx.lineTo(xFor(points.length - 1), yFor(0));
            ctx.lineTo(xFor(0), yFor(0));
            ctx.closePath();
            const gradient = ctx.createLinearGradient(
                0,
                0,
                0,
                ctx.canvas.height / (window.devicePixelRatio || 1)
            );
            gradient.addColorStop(0, hexToRgba(color, 0.18));
            gradient.addColorStop(1, hexToRgba(color, 0.01));
            ctx.fillStyle = gradient;
            ctx.fill();
        }
    }

    function observeChart() {
        if (!window.ResizeObserver || !elements.chart) {
            return;
        }
        const observer = new ResizeObserver(function () {
            drawHistory();
        });
        observer.observe(elements.chart.parentElement);
    }

    function clampRefresh(value) {
        const number = Number(value);
        if (!Number.isFinite(number)) {
            return DEFAULT_REFRESH_SECONDS;
        }
        return Math.max(MIN_REFRESH_SECONDS, Math.min(MAX_REFRESH_SECONDS, Math.round(number)));
    }

    function clampPercent(value) {
        const number = Number(value);
        if (!Number.isFinite(number)) {
            return 0;
        }
        return Math.max(0, Math.min(100, number));
    }

    function formatPercent(value) {
        const number = clampPercent(value);
        return number.toFixed(1).replace(/\.0$/, "");
    }

    function formatBytes(value) {
        const bytes = Number(value);
        if (!Number.isFinite(bytes) || bytes <= 0) {
            return "0 B";
        }
        const units = ["B", "KiB", "MiB", "GiB", "TiB", "PiB"];
        const exponent = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
        const amount = bytes / Math.pow(1024, exponent);
        const digits = amount >= 10 || exponent === 0 ? 0 : 1;
        return amount.toFixed(digits).replace(/\.0$/, "") + " " + units[exponent];
    }

    function formatHz(value) {
        const hz = Number(value);
        if (!Number.isFinite(hz) || hz <= 0) {
            return "";
        }
        if (hz >= 1e9) {
            return (hz / 1e9).toFixed(1).replace(/\.0$/, "") + " GHz";
        }
        if (hz >= 1e6) {
            return Math.round(hz / 1e6) + " MHz";
        }
        return Math.round(hz) + " Hz";
    }

    function formatDuration(seconds) {
        const total = Math.max(0, Math.floor(Number(seconds) || 0));
        const days = Math.floor(total / 86400);
        const hours = Math.floor((total % 86400) / 3600);
        const minutes = Math.floor((total % 3600) / 60);
        const secs = total % 60;
        const clock = [hours, minutes, secs].map(function (part) {
            return String(part).padStart(2, "0");
        }).join(":");
        return days > 0 ? days + "d " + clock : clock;
    }

    function formatTime(timestamp) {
        const date = new Date(Number(timestamp) || Date.now());
        return date.toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            hour12: false
        });
    }

    function bytePair(used, total) {
        return formatBytes(used) + " / " + formatBytes(total);
    }

    function joinNonEmpty(parts, separator) {
        return parts.filter(function (part) {
            return part !== null && part !== undefined && String(part).trim() !== "";
        }).join(separator || " · ");
    }

    function hexToRgba(hex, alpha) {
        const normalized = hex.replace("#", "");
        if (normalized.length !== 3 && normalized.length !== 6) {
            return "rgba(109, 93, 252, " + alpha + ")";
        }
        const expanded = normalized.length === 3
            ? normalized.split("").map(function (char) { return char + char; }).join("")
            : normalized;
        const number = parseInt(expanded, 16);
        return "rgba(" + ((number >> 16) & 255) + ", " + ((number >> 8) & 255) + ", " + (number & 255) + ", " + alpha + ")";
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
}());
