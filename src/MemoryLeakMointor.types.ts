import { EventSubscription } from "expo-modules-core";

// Basic memory info type
export type MemoryInfo = {
  totalMemory: number;
  availableMemory: number;
  usedMemory: number;
  appMemory: number;
  isLowMemory: boolean;
  lowMemoryThreshold: number;
};

// Memory readings for leak detection
export type MemoryReadings = number[];

// Session data type
export type SessionData = {
  sessionElapsed: number;
  sessionRemaining: number;
};

// Component analysis in session report
export type ComponentAnalysis = {
  componentName: string;
  totalIncrease: number;
  averageIncrease: number;
  initialMemory: number;
  finalMemory: number;
  peakMemory: number;
  readingCount: number;
};

// Session report type
export type SessionReport = {
  sessionDuration: number;
  componentsAnalyzed: number;
  leakingComponents: ComponentAnalysis[];
  timestamp: number;
};

// Event payload types
export type MemoryUpdateEventPayload = {
  memoryInfo: MemoryInfo;
  timestamp: number;
};

export type OnChangeEventPayload = {
  value: string;
};

export type LeakDetectedEventPayload = {
  componentName: string;
  totalIncrease: number;
  currentMemory: number;
  initialMemory: number;
  memoryReadings: MemoryReadings;
  timestamp: number;
  isFirstReport: boolean;
  sessionData?: SessionData;
};

export type SessionCompleteEventPayload = SessionReport;

export interface MemoryLeakMonitorEvents {
  onMemoryUpdate: MemoryUpdateEventPayload;
  onChange: OnChangeEventPayload;
  onLeakDetected: LeakDetectedEventPayload;
  onSessionComplete: SessionCompleteEventPayload;
}

export interface MemoryLeakMonitorModule {
  // Constants
  readonly MEMORY_UNITS: string;
  readonly UPDATE_INTERVAL: number;
  readonly PI: number;

  // Memory monitoring methods
  setValueAsync(value: string): Promise<void>;
  getMemoryInfo(): Promise<MemoryInfo>;
  startMemoryMonitoring(intervalMs: number): Promise<string>;
  stopMemoryMonitoring(): Promise<string>;

  // Component tracking methods
  startComponentTracking(componentName: string): Promise<string>;
  stopComponentTracking(componentId: string): Promise<string>;
  resetLeakTracking(): Promise<string>;

  // Session management methods
  startSession(durationMinutes: number): Promise<string>;
  stopSession(): Promise<SessionReport>;

  // Event methods
  addListener<K extends keyof MemoryLeakMonitorEvents>(
    eventName: K,
    listener: (event: MemoryLeakMonitorEvents[K]) => void
  ): EventSubscription;

  removeSubscription(subscription: EventSubscription): void;
  removeAllListeners(eventName?: keyof MemoryLeakMonitorEvents): void;
}

// Helper types for leak detection
export type LeakStatus = {
  isLeaking: boolean;
  memoryIncrease: number;
};

export type ComponentMemorySnapshot = {
  baselineMemory: number;
  previousMemory: number;
  peakMemory: number;
  readings: MemoryReadings;
  lastUpdateTime: number;
};

// Export the module instance type
export default MemoryLeakMonitorModule;
