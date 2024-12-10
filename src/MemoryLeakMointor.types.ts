import { EventSubscription } from "expo-modules-core";

// Event payload types
export type MemoryInfo = {
  totalMemory: number;
  availableMemory: number;
  usedMemory: number;
  appMemory: number;
  isLowMemory: boolean;
  lowMemoryThreshold: number;
};

export type MemoryUpdateEventPayload = {
  memoryInfo: MemoryInfo;
  timestamp: number;
};

export type OnChangeEventPayload = {
  value: string;
};

// Module events map
export interface MemoryLeakMonitorEvents {
  onMemoryUpdate: MemoryUpdateEventPayload;
  onChange: OnChangeEventPayload;
}

// Module interface
export interface MemoryLeakMonitorModule {
  // Constants
  readonly MEMORY_UNITS: string;
  readonly UPDATE_INTERVAL: number;
  readonly PI: number;

  // Methods
  hello(): Promise<string>;
  setValueAsync(value: string): Promise<void>;
  getMemoryInfo(): Promise<MemoryInfo>;
  startMemoryMonitoring(intervalMs: number): Promise<string>;
  stopMemoryMonitoring(): Promise<string>;

  // Event methods
  addListener<K extends keyof MemoryLeakMonitorEvents>(
    eventName: K,
    listener: (event: MemoryLeakMonitorEvents[K]) => void
  ): EventSubscription;

  removeSubscription(subscription: EventSubscription): void;
  removeAllListeners(eventName?: keyof MemoryLeakMonitorEvents): void;
}

// Export the module instance type
export default MemoryLeakMonitorModule;
