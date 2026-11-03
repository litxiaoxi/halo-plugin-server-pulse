const BYTE_UNITS = ["B", "KiB", "MiB", "GiB", "TiB", "PiB"];

export function clampPercent(value: number): number {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return 0;
  }
  return Math.min(100, Math.max(0, number));
}

export function formatPercent(value: number): string {
  return clampPercent(value).toFixed(1).replace(/\.0$/, "");
}

export function formatBytes(value: number): string {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return "0 B";
  }
  const exponent = Math.min(
    BYTE_UNITS.length - 1,
    Math.floor(Math.log(bytes) / Math.log(1024)),
  );
  const amount = bytes / 1024 ** exponent;
  const digits = amount >= 10 || exponent === 0 ? 0 : 1;
  return `${amount.toFixed(digits).replace(/\.0$/, "")} ${BYTE_UNITS[exponent]}`;
}

export function formatUptime(seconds: number): string {
  const total = Math.max(0, Math.floor(Number(seconds) || 0));
  const days = Math.floor(total / 86400);
  const hours = Math.floor((total % 86400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);

  if (days > 0) {
    return `${days} 天 ${hours} 小时`;
  }
  if (hours > 0) {
    return `${hours} 小时 ${minutes} 分`;
  }
  if (minutes > 0) {
    return `${minutes} 分`;
  }
  return `${total} 秒`;
}

export function formatClock(timestamp: number): string {
  const date = new Date(Number(timestamp) || Date.now());
  return date.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
}

export function bytePair(used: number, total: number): string {
  if (!total || total <= 0) {
    return "—";
  }
  return `${formatBytes(used)} / ${formatBytes(total)}`;
}
