import type { MetricsResponse } from "@/types";

const METRICS_URL = "/apis/api.serverpulse.halo.run/v1alpha1/metrics";
const REQUEST_TIMEOUT_MS = 5000;

export type MetricsResult =
  | { kind: "ok"; data: MetricsResponse }
  | { kind: "unauthorized" }
  | { kind: "forbidden"; message: string }
  | { kind: "warming" }
  | { kind: "error"; message: string };

async function readMessage(response: Response, fallback: string): Promise<string> {
  try {
    const body = (await response.json()) as { message?: unknown };
    if (typeof body?.message === "string" && body.message.trim()) {
      return body.message;
    }
  } catch {
    // Ignore malformed error bodies.
  }
  return fallback;
}

/**
 * Reads the existing ServerPulse metrics endpoint. The endpoint already applies
 * the plugin's access policy, so this function deliberately maps 401/403/503 to
 * distinct states instead of pretending every failure is a network error.
 */
export async function fetchMetrics(signal?: AbortSignal): Promise<MetricsResult> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  const forwardAbort = () => controller.abort();
  signal?.addEventListener("abort", forwardAbort, { once: true });

  try {
    const response = await fetch(METRICS_URL, {
      credentials: "same-origin",
      cache: "no-store",
      headers: { Accept: "application/json" },
      signal: controller.signal,
    });

    if (response.status === 401) {
      return { kind: "unauthorized" };
    }
    if (response.status === 403) {
      return {
        kind: "forbidden",
        message: await readMessage(response, "当前账号没有查看服务器监控的权限"),
      };
    }
    if (response.status === 503) {
      return { kind: "warming" };
    }
    if (!response.ok) {
      return { kind: "error", message: `请求失败（${response.status}）` };
    }

    return { kind: "ok", data: (await response.json()) as MetricsResponse };
  } catch {
    return {
      kind: "error",
      message: controller.signal.aborted ? "请求超时，请稍后重试" : "无法连接服务器",
    };
  } finally {
    window.clearTimeout(timeout);
    signal?.removeEventListener("abort", forwardAbort);
  }
}
