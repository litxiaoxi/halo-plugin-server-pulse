<script setup lang="ts">
import { computed } from "vue";
import { formatPercent } from "@/utils/format";

const props = withDefaults(
  defineProps<{
    percent: number;
    color: string;
    label: string;
  }>(),
  {
    percent: 0,
    color: "#6d5dfc",
    label: "",
  },
);

const RADIUS = 26;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

const clamped = computed(() => {
  const value = Number(props.percent);
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.min(100, Math.max(0, value));
});

const displayPercent = computed(() => formatPercent(clamped.value));
const offset = computed(() => CIRCUMFERENCE * (1 - clamped.value / 100));
</script>

<template>
  <div
    class="sp-ring"
    role="progressbar"
    :aria-label="label"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-valuenow="Math.round(clamped)"
  >
    <svg viewBox="0 0 64 64" aria-hidden="true">
      <circle class="sp-ring-track" cx="32" cy="32" :r="RADIUS" />
      <circle
        class="sp-ring-progress"
        cx="32"
        cy="32"
        :r="RADIUS"
        :stroke="color"
        :style="{
          strokeDasharray: String(CIRCUMFERENCE),
          strokeDashoffset: String(offset),
        }"
      />
    </svg>
    <span class="sp-ring-value">
      <strong>{{ displayPercent }}</strong><small>%</small>
    </span>
  </div>
</template>

<style>
.sp-ring {
  position: relative;
  flex: none;
  width: 58px;
  height: 58px;
  color: #101828;
  font-family: var(--sp-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace);
}

.sp-ring > svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.sp-ring-track {
  fill: none;
  stroke: #eef0f5;
  stroke-width: 5.5;
}

.sp-ring-progress {
  fill: none;
  stroke-width: 5.5;
  stroke-linecap: round;
  transition: stroke-dashoffset 700ms cubic-bezier(0.22, 1, 0.36, 1);
}

.sp-ring-value {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1px;
}

.sp-ring-value strong {
  font-size: 15px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.sp-ring-value small {
  color: #98a2b3;
  font-size: 9px;
  font-weight: 500;
  line-height: 1;
  transform: translateY(4px);
}

@media (prefers-reduced-motion: reduce) {
  .sp-ring-progress {
    transition: none;
  }
}
</style>
