export interface ProcessorInfo {
  name: string;
  cores: number;
  clockHz: number;
  bitDepth: number;
  architecture: string;
}

export interface OsInfo {
  family: string;
  version: string;
  codeName: string;
  bitness: number;
}

export interface MemoryInfo {
  totalBytes: number;
  type: string;
}

export interface StorageInfo {
  model: string;
  mount: string;
  diskCount: number;
  totalBytes: number;
  swapTotalBytes: number;
}

export interface StaticInfo {
  processor: ProcessorInfo;
  os: OsInfo;
  memory: MemoryInfo;
  storage: StorageInfo;
  processCount: number;
}

export interface Usage {
  cpuPercent: number;
  memoryPercent: number;
  storagePercent: number;
  memoryUsedBytes: number;
  memoryTotalBytes: number;
  storageUsedBytes: number;
  storageTotalBytes: number;
  uptimeSeconds: number;
}

export interface UiSettings {
  theme: string;
  refreshInterval: number;
  showHistory: boolean;
  showDetails: boolean;
}

export interface AccessInfo {
  mode: string;
  authenticated: boolean;
}

export interface HistoryPoint {
  timestamp: number;
  cpuPercent: number;
  memoryPercent: number;
  storagePercent: number;
}

export interface MetricsResponse {
  serverName: string;
  pluginVersion: string;
  generatedAt: number;
  stale: boolean;
  degraded: boolean;
  access: AccessInfo;
  ui: UiSettings;
  info: StaticInfo | null;
  usage: Usage;
  runtime: unknown;
  history: HistoryPoint[];
}

export interface WidgetConfig {
  title?: string;
  showUptime?: boolean;
  showHost?: boolean;
}
