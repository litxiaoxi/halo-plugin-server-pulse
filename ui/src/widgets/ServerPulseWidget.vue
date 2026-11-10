<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import MetricRing from "@/components/MetricRing.vue";
import SparklineChart from "@/components/SparklineChart.vue";
import { fetchMetrics } from "@/api/metrics";
import type { HistoryPoint, MetricsResponse, WidgetConfig } from "@/types";
import {
  bytePair,
  clampPercent,
  formatClock,
  formatPercent,
  formatUptime,
} from "@/utils/format";

const props = withDefaults(
  defineProps<{
    config?: WidgetConfig;
    editMode?: boolean;
    previewMode?: boolean;
  }>(),
  {
    config: () => ({}),
    editMode: false,
    previewMode: false,
  },
);

type Status = "loading" | "ok" | "warming" | "unauthorized" | "forbidden" | "error";
type Tone = "live" | "warn" | "muted";

const DEFAULT_REFRESH_SECONDS = 2;

const SAMPLE_HISTORY: HistoryPoint[] = Array.from({ length: 60 }, (_, index) => {
  const now = Date.now();
  return {
    timestamp: now - (60 - index) * 1000,
    cpuPercent: Math.max(4, 42.3 + Math.sin(index / 4.2) * 7 + Math.sin(index / 1.7) * 2),
    memoryPercent: 63.8 + Math.sin(index / 5.5) * 2.6,
    storagePercent: 71.2 + index * 0.004,
  };
});

const SAMPLE: MetricsResponse = {
  serverName: "生产服务器",
  pluginVersion: "1.3.0",
  generatedAt: Date.now(),
  stale: false,
  degraded: false,
  access: { mode: "login", authenticated: true },
  ui: { theme: "auto", refreshInterval: 2, showHistory: true, showDetails: true },
  info: {
    processor: {
      name: "AMD EPYC 7B13",
      cores: 16,
      clockHz: 2_450_000_000,
      bitDepth: 64,
      architecture: "amd64",
    },
    os: { family: "Linux", version: "6.1.0", codeName: "", bitness: 64 },
    memory: { totalBytes: 16 * 1024 ** 3, type: "DDR4" },
    storage: {
      model: "NVMe SSD",
      mount: "/",
      diskCount: 1,
      totalBytes: 480 * 1024 ** 3,
      swapTotalBytes: 0,
    },
    processCount: 128,
  },
  usage: {
    cpuPercent: 42.3,
    memoryPercent: 63.8,
    storagePercent: 71.2,
    memoryUsedBytes: 10.2 * 1024 ** 3,
    memoryTotalBytes: 16 * 1024 ** 3,
    storageUsedBytes: 340 * 1024 ** 3,
    storageTotalBytes: 480 * 1024 ** 3,
    uptimeSeconds: 3 * 86400 + 4 * 3600 + 12 * 60,
  },
  runtime: null,
  history: SAMPLE_HISTORY,
};

const snapshot = ref<MetricsResponse | null>(null);
const status = ref<Status>("loading");
const message = ref("");
const refreshSeconds = ref(DEFAULT_REFRESH_SECONDS);

let timer: number | undefined;
let inFlight: AbortController | null = null;
let disposed = false;
let requestId = 0;
let failures = 0;

const active = computed(() => (props.previewMode ? SAMPLE : snapshot.value));
const usage = computed(() => active.value?.usage ?? null);
const info = computed(() => active.value?.info ?? null);

const showUptime = computed(() => props.config?.showUptime !== false);
const showHost = computed(() => props.config?.showHost !== false);

const cpuPercent = computed(() => clampPercent(usage.value?.cpuPercent ?? 0));
const memoryPercent = computed(() => clampPercent(usage.value?.memoryPercent ?? 0));
const storagePercent = computed(() => clampPercent(usage.value?.storagePercent ?? 0));

const history = computed<HistoryPoint[]>(() => {
  const raw = active.value?.history;
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw.filter(
    (point) =>
      point &&
      Number.isFinite(point.cpuPercent) &&
      Number.isFinite(point.memoryPercent) &&
      Number.isFinite(point.storagePercent),
  );
});

const title = computed(() => {
  const configured = String(props.config?.title ?? "").trim();
  if (configured) {
    return configured;
  }
  return active.value?.serverName?.trim() || "服务器状态";
});

const cpuCores = computed(() => {
  const cores = info.value?.processor?.cores ?? 0;
  return cores > 0 ? `${cores} 核` : "处理器";
});

const cpuModel = computed(() => {
  const name = info.value?.processor?.name;
  return showHost.value && name ? ` · ${name}` : "";
});

const cpuDetail = computed(() => `${cpuCores.value}${cpuModel.value}`.trim());

const memoryDetail = computed(() =>
  usage.value ? bytePair(usage.value.memoryUsedBytes, usage.value.memoryTotalBytes) : "—",
);

const storageDetail = computed(() =>
  usage.value ? bytePair(usage.value.storageUsedBytes, usage.value.storageTotalBytes) : "—",
);

const uptimeText = computed(() =>
  usage.value ? formatUptime(usage.value.uptimeSeconds) : "—",
);

const updatedText = computed(() => {
  const generatedAt = active.value?.generatedAt;
  return generatedAt ? `更新于 ${formatClock(generatedAt)}` : "";
});

const pill = computed<{ label: string; tone: Tone; title: string }>(() => {
  if (props.previewMode) {
    return { label: "预览", tone: "muted", title: "示例数据，不会请求服务器" };
  }
  const current = snapshot.value;
  if (current && status.value === "ok") {
    if (current.degraded) {
      return {
        label: "降级",
        tone: "warn",
        title: "部分硬件信息不可用，已降级为 JVM 基础指标",
      };
    }
    if (current.stale) {
      return { label: "数据延迟", tone: "warn", title: "最近一次采样已超过 10 秒" };
    }
    return { label: "实时", tone: "live", title: `每 ${refreshSeconds.value} 秒刷新` };
  }
  switch (status.value) {
    case "warming":
      return { label: "初始化", tone: "muted", title: "监控数据正在初始化" };
    case "forbidden":
      return { label: "无权限", tone: "muted", title: message.value || "当前账号没有查看权限" };
    case "unauthorized":
      return { label: "未登录", tone: "muted", title: "登录状态已失效" };
    case "error":
      return { label: "连接中断", tone: "warn", title: message.value || "无法连接监控接口" };
    default:
      return { label: "连接中", tone: "muted", title: "正在读取服务器状态" };
  }
});

const showSkeleton = computed(
  () =>
    !props.previewMode &&
    !snapshot.value &&
    (status.value === "loading" || status.value === "ok"),
);

const panelTitle = computed(() => {
  switch (status.value) {
    case "warming":
      return "正在初始化";
    case "forbidden":
      return "无查看权限";
    case "unauthorized":
      return "登录状态已失效";
    case "error":
      return "暂时无法获取数据";
    default:
      return "加载中";
  }
});

const panelText = computed(() => {
  switch (status.value) {
    case "warming":
      return "监控数据正在准备，稍后会自动刷新。";
    case "forbidden":
      return message.value || "当前账号没有查看服务器监控的权限，请联系站点管理员。";
    case "unauthorized":
      return "请刷新页面重新登录后再查看服务器状态。";
    case "error":
      return message.value || "请检查网络连接或稍后重试。";
    default:
      return "";
  }
});

const canRetry = computed(() => status.value !== "warming");
const retryLabel = computed(() => (status.value === "unauthorized" ? "刷新页面" : "重试"));

function clearTimer(): void {
  if (timer !== undefined) {
    window.clearTimeout(timer);
    timer = undefined;
  }
}

function abortInFlight(): void {
  inFlight?.abort();
  inFlight = null;
}

function schedule(delay: number): void {
  clearTimer();
  if (disposed || props.previewMode) {
    return;
  }
  if (typeof document !== "undefined" && document.hidden) {
    return;
  }
  timer = window.setTimeout(() => {
    void load();
  }, Math.max(1000, Math.min(30000, delay)));
}

function applyOk(data: MetricsResponse): void {
  snapshot.value = data;
  status.value = "ok";
  message.value = "";
  failures = 0;

  const interval = Number(data.ui?.refreshInterval);
  refreshSeconds.value = Number.isFinite(interval)
    ? Math.max(1, Math.min(30, interval))
    : DEFAULT_REFRESH_SECONDS;
  schedule(refreshSeconds.value * 1000);
}

async function load(): Promise<void> {
  if (disposed || props.previewMode) {
    return;
  }
  clearTimer();
  abortInFlight();

  const id = ++requestId;
  const controller = new AbortController();
  inFlight = controller;
  const result = await fetchMetrics(controller.signal);

  if (id !== requestId || disposed) {
    return;
  }
  if (typeof document !== "undefined" && document.hidden) {
    return;
  }
  if (inFlight === controller) {
    inFlight = null;
  }

  switch (result.kind) {
    case "ok":
      applyOk(result.data);
      return;
    case "warming":
      status.value = "warming";
      schedule(1000);
      return;
    case "forbidden":
      snapshot.value = null;
      status.value = "forbidden";
      message.value = result.message;
      return;
    case "unauthorized":
      snapshot.value = null;
      status.value = "unauthorized";
      message.value = "";
      schedule(30000);
      return;
    case "error":
      failures += 1;
      status.value = "error";
      message.value = result.message;
      schedule(Math.min(30000, 3000 * 2 ** Math.min(failures - 1, 4)));
      return;
  }
}

function onRetry(): void {
  if (status.value === "unauthorized") {
    window.location.reload();
    return;
  }
  clearTimer();
  void load();
}

function onVisibilityChange(): void {
  if (disposed || props.previewMode) {
    return;
  }
  if (document.hidden) {
    clearTimer();
    abortInFlight();
    return;
  }
  void load();
}

function onFocus(): void {
  if (disposed || props.previewMode || document.hidden) {
    return;
  }
  void load();
}

onMounted(() => {
  if (props.previewMode) {
    return;
  }
  document.addEventListener("visibilitychange", onVisibilityChange);
  window.addEventListener("focus", onFocus);
  void load();
});

onUnmounted(() => {
  disposed = true;
  clearTimer();
  abortInFlight();
  document.removeEventListener("visibilitychange", onVisibilityChange);
  window.removeEventListener("focus", onFocus);
});
</script>

<template>
  <WidgetCard body-class="sp-widget-body">
    <template #title>
      <span class="sp-title" :title="title">{{ title }}</span>
    </template>

    <template #actions>
      <span
        class="sp-pill"
        :class="`sp-pill--${pill.tone}`"
        :title="pill.title"
        aria-live="polite"
      >
        <i class="sp-dot" aria-hidden="true"></i>{{ pill.label }}
      </span>
    </template>

    <div class="sp-shell">
      <template v-if="active && usage">
        <div class="sp-content" :class="{ 'sp-has-chart': history.length >= 2 }">
          <div class="sp-metrics">
            <section class="sp-metric sp-metric--cpu" aria-label="CPU 使用率">
              <MetricRing
                class="sp-ring-slot"
                :percent="cpuPercent"
                color="#6d5dfc"
                label="CPU 使用率"
              />
              <div class="sp-metric-body">
                <div class="sp-metric-top">
                  <span class="sp-label"><i class="sp-label-dot"></i>CPU</span>
                  <span class="sp-percent">{{ formatPercent(cpuPercent) }}<small>%</small></span>
                </div>
                <div class="sp-bar" aria-hidden="true">
                  <span :style="{ width: `${cpuPercent}%` }"></span>
                </div>
                <span class="sp-sub" :title="cpuDetail">{{ cpuCores }}<span class="sp-sub-model">{{ cpuModel }}</span></span>
              </div>
            </section>

            <section class="sp-metric sp-metric--memory" aria-label="内存使用率">
              <MetricRing
                class="sp-ring-slot"
                :percent="memoryPercent"
                color="#0ea5e9"
                label="内存使用率"
              />
              <div class="sp-metric-body">
                <div class="sp-metric-top">
                  <span class="sp-label"><i class="sp-label-dot"></i>内存</span>
                  <span class="sp-percent">{{ formatPercent(memoryPercent) }}<small>%</small></span>
                </div>
                <div class="sp-bar" aria-hidden="true">
                  <span :style="{ width: `${memoryPercent}%` }"></span>
                </div>
                <span class="sp-sub" :title="memoryDetail">{{ memoryDetail }}</span>
              </div>
            </section>

            <section class="sp-metric sp-metric--storage" aria-label="磁盘使用率">
              <MetricRing
                class="sp-ring-slot"
                :percent="storagePercent"
                color="#f59e0b"
                label="磁盘使用率"
              />
              <div class="sp-metric-body">
                <div class="sp-metric-top">
                  <span class="sp-label"><i class="sp-label-dot"></i>磁盘</span>
                  <span class="sp-percent">{{ formatPercent(storagePercent) }}<small>%</small></span>
                </div>
                <div class="sp-bar" aria-hidden="true">
                  <span :style="{ width: `${storagePercent}%` }"></span>
                </div>
                <span class="sp-sub" :title="storageDetail">{{ storageDetail }}</span>
              </div>
            </section>
          </div>

          <div class="sp-chart-slot">
            <SparklineChart v-if="history.length >= 2" :history="history" />
          </div>
        </div>

        <footer v-if="showUptime || showHost" class="sp-footer">
          <span v-if="showUptime" class="sp-footer-item">
            <svg class="sp-icon" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="8.5"></circle>
              <path d="M12 7.5V12l3 1.8"></path>
            </svg>
            已运行 {{ uptimeText }}
          </span>
          <span v-else class="sp-footer-item">ServerPulse</span>
          <span class="sp-footer-item sp-footer-updated">{{ updatedText }}</span>
        </footer>
      </template>

      <div v-else-if="showSkeleton" class="sp-content sp-content--skeleton" aria-hidden="true">
        <div class="sp-metrics">
          <div v-for="index in 3" :key="index" class="sp-metric sp-metric--skeleton">
            <div class="sp-skeleton-ring"></div>
            <div class="sp-metric-body">
              <div class="sp-skeleton-line sp-skeleton-line--label"></div>
              <div class="sp-skeleton-line sp-skeleton-line--sub"></div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="sp-state">
        <div class="sp-state-icon" aria-hidden="true">
          <svg v-if="status === 'warming'" class="sp-spinner" viewBox="0 0 24 24">
            <circle cx="12" cy="12" r="8"></circle>
            <path d="M12 4a8 8 0 0 1 8 8"></path>
          </svg>
          <svg v-else-if="status === 'forbidden' || status === 'unauthorized'" viewBox="0 0 24 24">
            <rect x="5" y="10" width="14" height="9" rx="2"></rect>
            <path d="M8.5 10V7.5a3.5 3.5 0 0 1 7 0V10"></path>
          </svg>
          <svg v-else viewBox="0 0 24 24">
            <path d="M12 3.5 21 20H3L12 3.5Z"></path>
            <path d="M12 9.5v4.5M12 17.2v.1"></path>
          </svg>
        </div>
        <p class="sp-state-title">{{ panelTitle }}</p>
        <p v-if="panelText" class="sp-state-text">{{ panelText }}</p>
        <button v-if="canRetry" class="sp-state-button" type="button" @click="onRetry">
          {{ retryLabel }}
        </button>
      </div>
    </div>
  </WidgetCard>
</template>

<style>
.sp-widget-body {
  --sp-mono: ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace;
  padding: 12px 16px 10px;
}

.sp-title {
  display: block;
  overflow: hidden;
  color: #101828;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sp-shell {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
  min-height: 0;
  container-type: size;
}

.sp-content {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  gap: 12px;
  min-height: 0;
}

.sp-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  min-height: 0;
}

.sp-metric {
  position: relative;
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
  padding: 14px;
  overflow: hidden;
  border-radius: 14px;
  background: linear-gradient(150deg, var(--sp-accent-soft) 0%, rgba(255, 255, 255, 0) 76%);
  box-shadow: inset 0 0 0 1px var(--sp-accent-border);
}

.sp-metric--cpu {
  --sp-accent: #6d5dfc;
  --sp-accent-soft: rgba(109, 93, 252, 0.085);
  --sp-accent-border: rgba(109, 93, 252, 0.14);
}

.sp-metric--memory {
  --sp-accent: #0ea5e9;
  --sp-accent-soft: rgba(14, 165, 233, 0.08);
  --sp-accent-border: rgba(14, 165, 233, 0.14);
}

.sp-metric--storage {
  --sp-accent: #f59e0b;
  --sp-accent-soft: rgba(245, 158, 11, 0.085);
  --sp-accent-border: rgba(245, 158, 11, 0.16);
}

.sp-metric--skeleton {
  background: #f8fafc;
  box-shadow: inset 0 0 0 1px #f0f2f6;
}

.sp-metric .sp-ring {
  width: 62px;
  height: 62px;
  flex: none;
}

.sp-metric .sp-ring-value strong {
  font-size: 16px;
}

.sp-metric-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.sp-metric-top {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.sp-label {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  color: #475467;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
}

.sp-label-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sp-accent);
  box-shadow: 0 0 0 2px var(--sp-accent-soft);
}

.sp-percent {
  display: none;
  align-items: baseline;
  color: #101828;
  font-family: var(--sp-mono);
  font-size: 22px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1;
  white-space: nowrap;
}

.sp-percent small {
  margin-left: 1px;
  color: #98a2b3;
  font-size: 11px;
  font-weight: 500;
}

.sp-bar {
  display: none;
  height: 4px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(16, 24, 40, 0.06);
}

.sp-bar > span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--sp-accent);
  transition: width 600ms cubic-bezier(0.22, 1, 0.36, 1);
}

.sp-sub {
  overflow: hidden;
  color: #98a2b3;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sp-chart-slot {
  display: none;
  min-width: 0;
  min-height: 0;
}

.sp-footer {
  display: flex;
  flex: none;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid #f2f4f7;
  color: #667085;
  font-size: 11.5px;
  line-height: 1.3;
}

.sp-footer-item {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sp-footer-updated {
  flex: none;
  color: #98a2b3;
  font-variant-numeric: tabular-nums;
}

.sp-icon {
  flex: none;
  width: 13px;
  height: 13px;
  fill: none;
  opacity: 0.75;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.sp-pill {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}

.sp-pill--live {
  color: #067647;
  background: #ecfdf3;
}

.sp-pill--warn {
  color: #b54708;
  background: #fffaeb;
}

.sp-pill--muted {
  color: #667085;
  background: #f2f4f7;
}

.sp-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.sp-pill--live .sp-dot {
  animation: sp-pulse 1.8s ease-out infinite;
}

.sp-state {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 5px;
  align-items: center;
  justify-content: center;
  min-height: 0;
  text-align: center;
}

.sp-state-icon {
  display: grid;
  width: 30px;
  height: 30px;
  margin-bottom: 1px;
  place-items: center;
  border-radius: 9px;
  background: #f2f4f7;
  color: #667085;
}

.sp-state-icon svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.sp-spinner {
  animation: sp-spin 0.9s linear infinite;
}

.sp-state-title {
  margin: 0;
  color: #344054;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
}

.sp-state-text {
  max-width: 36em;
  margin: 0;
  color: #98a2b3;
  font-size: 11.5px;
  line-height: 1.5;
}

.sp-state-button {
  height: 26px;
  margin-top: 2px;
  padding: 0 12px;
  border: 1px solid #d0d5dd;
  border-radius: 7px;
  background: #fff;
  color: #344054;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 120ms ease, border-color 120ms ease;
}

.sp-state-button:hover {
  border-color: #b8c0cc;
  background: #f9fafb;
}

.sp-skeleton-ring {
  flex: none;
  width: 62px;
  height: 62px;
  border-radius: 50%;
  background: linear-gradient(90deg, #f2f4f7 25%, #e9edf3 37%, #f2f4f7 63%);
  background-size: 400% 100%;
  animation: sp-shimmer 1.4s ease infinite;
}

.sp-skeleton-line {
  height: 8px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f2f4f7 25%, #e9edf3 37%, #f2f4f7 63%);
  background-size: 400% 100%;
  animation: sp-shimmer 1.4s ease infinite;
}

.sp-skeleton-line--label {
  width: 44px;
}

.sp-skeleton-line--sub {
  width: 68px;
}

@keyframes sp-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(18, 183, 106, 0.35);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(18, 183, 106, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(18, 183, 106, 0);
  }
}

@keyframes sp-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

@keyframes sp-spin {
  to {
    transform: rotate(360deg);
  }
}

/* Medium-narrow cards: keep three columns but tighten the inner spacing. */
@container (min-width: 480px) and (max-width: 649px) {
  .sp-metric {
    gap: 8px;
    padding: 10px;
  }

  .sp-metric .sp-ring {
    width: 50px;
    height: 50px;
  }

  .sp-metric .sp-ring-value strong {
    font-size: 13px;
  }

  .sp-label {
    font-size: 11.5px;
  }

  .sp-sub {
    font-size: 10.5px;
  }
}

/* Short containers: compact strip with large numbers and bars. */
@container (max-height: 154px) {
  .sp-metrics {
    gap: 12px;
  }

  .sp-metric {
    flex-direction: column;
    gap: 6px;
    align-items: stretch;
    justify-content: center;
    padding: 10px 12px;
  }

  .sp-ring-slot,
  .sp-skeleton-ring {
    display: none;
  }

  .sp-percent {
    display: inline-flex;
    font-size: 26px;
  }

  .sp-bar {
    display: block;
  }

  .sp-sub {
    display: none;
  }

  .sp-metric-top {
    justify-content: space-between;
  }

  .sp-content {
    gap: 10px;
  }

  .sp-footer {
    padding-top: 6px;
    font-size: 11px;
  }

  .sp-chart-slot {
    display: none;
  }
}

@container (max-width: 479px) and (max-height: 154px) {
  .sp-metric {
    padding: 8px 10px;
  }

  .sp-label {
    font-size: 11px;
  }

  .sp-percent {
    font-size: 24px;
  }
}

/* Narrow but slightly taller than the strip threshold: same strip layout. */
@container (max-width: 479px) and (min-height: 155px) and (max-height: 199px) {
  .sp-metrics {
    gap: 10px;
  }

  .sp-metric {
    flex-direction: column;
    gap: 6px;
    align-items: stretch;
    justify-content: center;
    padding: 10px;
  }

  .sp-ring-slot,
  .sp-skeleton-ring {
    display: none;
  }

  .sp-percent {
    display: inline-flex;
    font-size: 24px;
  }

  .sp-bar {
    display: block;
  }

  .sp-sub {
    display: none;
  }
}

/* Narrow: stack metrics as rows and use the vertical space. */
@container (max-width: 479px) and (min-height: 200px) {
  .sp-metrics {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }

  .sp-metric {
    flex-direction: row;
    gap: 10px;
    align-items: center;
    justify-content: flex-start;
    padding: 10px 12px;
  }

  .sp-metric-body {
    flex: 1;
  }

  .sp-ring-slot,
  .sp-skeleton-ring {
    display: none;
  }

  .sp-percent {
    display: inline-flex;
    font-size: 24px;
  }

  .sp-bar {
    display: block;
  }

  .sp-sub {
    display: none;
  }

  .sp-chart-slot {
    display: none;
  }
}

@container (max-width: 479px) and (min-height: 280px) {
  .sp-metric {
    padding: 12px 14px;
  }

  .sp-sub {
    display: block;
  }
}

@container (max-width: 479px) and (min-height: 380px) {
  .sp-content.sp-has-chart {
    grid-template-rows: minmax(0, 1fr) minmax(72px, 0.55fr);
  }

  .sp-content.sp-has-chart .sp-chart-slot {
    display: flex;
  }
}

/* Wide but not tall: metrics on the left, live trend on the right. */
@container (min-width: 900px) and (min-height: 155px) and (max-height: 259px) {
  .sp-content.sp-has-chart {
    grid-template-columns: minmax(0, 2.25fr) minmax(200px, 1fr);
  }

  .sp-content.sp-has-chart .sp-chart-slot {
    display: flex;
  }
}

/* Medium-tall cards: switch to a vertical gauge composition before the chart fits. */
@container (min-width: 480px) and (max-width: 699px) and (min-height: 155px) {
  .sp-sub-model {
    display: none;
  }
}

@container (min-width: 480px) and (max-width: 899px) and (min-height: 155px) and (max-height: 219px) {
  .sp-metric {
    flex-direction: column;
    gap: 6px;
    justify-content: center;
    padding: 10px;
  }

  .sp-metric .sp-ring {
    width: 60px;
    height: 60px;
  }

  .sp-metric .sp-ring-value strong {
    font-size: 15px;
  }

  .sp-metric-body {
    align-items: center;
  }

  .sp-metric-top {
    justify-content: center;
  }

  .sp-sub {
    text-align: center;
  }
}

@container (min-width: 480px) and (max-width: 899px) and (min-height: 220px) and (max-height: 259px) {
  .sp-content.sp-has-chart {
    grid-template-rows: minmax(0, 1fr) minmax(78px, 0.6fr);
  }

  .sp-content.sp-has-chart .sp-chart-slot {
    display: flex;
  }

  .sp-metric {
    flex-direction: column;
    gap: 8px;
    justify-content: center;
    padding: 12px;
  }

  .sp-metric .sp-ring {
    width: 76px;
    height: 76px;
  }

  .sp-metric .sp-ring-value strong {
    font-size: 18px;
  }

  .sp-metric-body {
    align-items: center;
  }

  .sp-metric-top {
    justify-content: center;
  }

  .sp-sub {
    text-align: center;
  }
}

/* Tall: large gauge cards with the trend below. */
@container (min-width: 480px) and (min-height: 260px) {
  .sp-content.sp-has-chart {
    grid-template-rows: minmax(0, 1fr) minmax(82px, 0.6fr);
  }

  .sp-content.sp-has-chart .sp-chart-slot {
    display: flex;
  }

  .sp-metric {
    flex-direction: column;
    gap: 10px;
    justify-content: center;
    padding: 16px;
  }

  .sp-metric .sp-ring {
    width: 82px;
    height: 82px;
  }

  .sp-metric .sp-ring-value strong {
    font-size: 19px;
  }

  .sp-metric-body {
    align-items: center;
  }

  .sp-metric-top {
    justify-content: center;
  }

  .sp-sub {
    max-width: 100%;
    text-align: center;
  }

  .sp-percent {
    display: none;
  }

  .sp-bar {
    display: none;
  }
}

@container (min-width: 480px) and (min-height: 360px) {
  .sp-metric .sp-ring {
    width: 100px;
    height: 100px;
  }

  .sp-metric .sp-ring-value strong {
    font-size: 23px;
  }

  .sp-metric .sp-ring-value small {
    font-size: 11px;
  }

  .sp-label {
    font-size: 12.5px;
  }

  .sp-sub {
    font-size: 11.5px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .sp-pill--live .sp-dot,
  .sp-spinner,
  .sp-skeleton-ring,
  .sp-skeleton-line {
    animation: none;
  }

  .sp-bar > span {
    transition: none;
  }
}
</style>
