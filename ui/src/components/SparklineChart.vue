<script setup lang="ts">
import { computed } from "vue";
import type { HistoryPoint } from "@/types";

const props = defineProps<{
  history: HistoryPoint[];
}>();

const SERIES = [
  { key: "cpuPercent", label: "CPU", color: "#6d5dfc" },
  { key: "memoryPercent", label: "内存", color: "#0ea5e9" },
  { key: "storagePercent", label: "磁盘", color: "#f59e0b" },
] as const;

const paths = computed(() => {
  const points = props.history;
  if (points.length < 2) {
    return [];
  }
  return SERIES.map((series) => {
    const coordinates = points.map((point, index) => {
      const value = Math.min(100, Math.max(0, Number(point[series.key]) || 0));
      const x = 1 + (index / (points.length - 1)) * 98;
      const y = 96 - (value / 100) * 92;
      return `${x.toFixed(2)},${y.toFixed(2)}`;
    });
    const line = coordinates
      .map((coordinate, index) => `${index === 0 ? "M" : "L"}${coordinate}`)
      .join(" ");
    return {
      ...series,
      line,
      area: `${line} L99,100 L1,100 Z`,
    };
  });
});
</script>

<template>
  <div class="sp-chart">
    <div class="sp-chart-head">
      <span class="sp-chart-title">最近 2 分钟</span>
      <span class="sp-legend">
        <span v-for="series in SERIES" :key="series.key" class="sp-legend-item">
          <i class="sp-legend-dot" :style="{ background: series.color }"></i>{{ series.label }}
        </span>
      </span>
    </div>
    <div class="sp-chart-body">
      <svg
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        role="img"
        aria-label="最近两分钟 CPU、内存、磁盘使用率趋势"
      >
        <line
          v-for="y in [25, 50, 75]"
          :key="y"
          x1="0"
          :y1="y"
          x2="100"
          :y2="y"
          class="sp-chart-grid"
          vector-effect="non-scaling-stroke"
        />
        <g v-for="series in paths" :key="series.key">
          <path :d="series.area" :fill="series.color" class="sp-chart-area" />
          <path
            :d="series.line"
            :stroke="series.color"
            class="sp-chart-line"
            vector-effect="non-scaling-stroke"
          />
        </g>
      </svg>
    </div>
  </div>
</template>

<style>
.sp-chart {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  min-height: 0;
  padding: 11px 13px 9px;
  border: 1px solid #eef1f6;
  border-radius: 14px;
  background: linear-gradient(180deg, #fbfcfe 0%, #f7f9fc 100%);
}

.sp-chart-head {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  align-items: center;
  justify-content: space-between;
}

.sp-chart-title {
  color: #667085;
  font-size: 11.5px;
  font-weight: 500;
  line-height: 1.2;
}

.sp-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #98a2b3;
  font-size: 11px;
  line-height: 1.2;
}

.sp-legend-item {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.sp-legend-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.sp-chart-body {
  flex: 1;
  min-height: 40px;
}

.sp-chart-body svg {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.sp-chart-grid {
  stroke: #e6eaf1;
  stroke-dasharray: 2 3;
  stroke-width: 1;
}

.sp-chart-area {
  opacity: 0.05;
}

.sp-chart-line {
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.6;
}
</style>
