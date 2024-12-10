// useLeakDetectionStore.ts
import { create } from "zustand";
import type { MemoryInfo } from "../MemoryLeakMointor.types";
import MemoryLeakMonitor from "../MemoryLeakMointorModule";

interface MemorySnapshot {
  baselineUsedMemory: number;
  previousUsedMemory: number;
  peakMemory: number;
  memoryReadings: number[];
  lastUpdateTime: number;
}

interface LeakDetectionState {
  memoryInfo: MemoryInfo | null;
  componentSnapshots: Record<string, MemorySnapshot>;
  subscribeToMemoryUpdates: () => () => void;
  trackComponent: (componentName: string, usedMemory: number) => void;
  getLeakStatus: (componentName: string) => {
    isLeaking: boolean;
    memoryIncrease: number;
  };
}

const LEAK_WINDOW_SIZE = 5; // Number of readings to keep
const SIGNIFICANT_INCREASE_MB = 10; // Memory increase threshold in MB
const MIN_MEMORY_CHANGE = 2; // Minimum memory change to consider (MB)

export const useLeakDetectionStore = create<LeakDetectionState>((set, get) => ({
  memoryInfo: null,
  componentSnapshots: {},

  subscribeToMemoryUpdates: () => {
    const subscription = MemoryLeakMonitor.addListener(
      "onMemoryUpdate",
      (event: { memoryInfo: MemoryInfo; timestamp: number }) => {
        set({ memoryInfo: event.memoryInfo });

        if (event.memoryInfo?.usedMemory) {
          const state = get();
          Object.keys(state.componentSnapshots).forEach((componentName) => {
            state.trackComponent(componentName, event.memoryInfo.usedMemory);
          });
        }
      }
    );
    return () => subscription.remove();
  },

  trackComponent: (componentName: string, currentUsedMemory: number) => {
    const { componentSnapshots } = get();
    const snapshot = componentSnapshots[componentName];
    const now = Date.now();

    if (!snapshot) {
      // Initialize first snapshot
      set((state) => ({
        componentSnapshots: {
          ...state.componentSnapshots,
          [componentName]: {
            baselineUsedMemory: currentUsedMemory,
            previousUsedMemory: currentUsedMemory,
            peakMemory: currentUsedMemory,
            memoryReadings: [currentUsedMemory],
            lastUpdateTime: now,
          },
        },
      }));
      console.log(
        `Started monitoring ${componentName} at ${currentUsedMemory}MB used memory`
      );
      return;
    }

    // Debounce updates (500ms)
    if (now - snapshot.lastUpdateTime < 500) return;

    const memoryIncrease = currentUsedMemory - snapshot.previousUsedMemory;

    // Only track significant changes
    if (Math.abs(memoryIncrease) >= MIN_MEMORY_CHANGE) {
      const newReadings = [...snapshot.memoryReadings, currentUsedMemory].slice(
        -LEAK_WINDOW_SIZE
      );
      const newPeakMemory = Math.max(currentUsedMemory, snapshot.peakMemory);

      set((state) => ({
        componentSnapshots: {
          ...state.componentSnapshots,
          [componentName]: {
            ...snapshot,
            previousUsedMemory: currentUsedMemory,
            peakMemory: newPeakMemory,
            memoryReadings: newReadings,
            lastUpdateTime: now,
          },
        },
      }));

      if (memoryIncrease >= SIGNIFICANT_INCREASE_MB) {
        console.log(
          `Memory increase in ${componentName}: ${memoryIncrease.toFixed(1)}MB (Total: ${currentUsedMemory}MB)`
        );
      }
    }
  },

  getLeakStatus: (componentName: string) => {
    const { componentSnapshots } = get();
    const snapshot = componentSnapshots[componentName];

    if (!snapshot || snapshot.memoryReadings.length < 2) {
      return { isLeaking: false, memoryIncrease: 0 };
    }

    const readings = snapshot.memoryReadings;
    const totalIncrease = readings[readings.length - 1] - readings[0];

    // Check for consistently increasing trend
    const increasingTrend = readings.every(
      (val, i) => i === 0 || val >= readings[i - 1] - MIN_MEMORY_CHANGE // Allow for small fluctuations
    );

    const isLeaking =
      totalIncrease >= SIGNIFICANT_INCREASE_MB &&
      increasingTrend &&
      readings.length >= 3;

    if (isLeaking) {
      console.warn(
        `‼️ MEMORY LEAK DETECTED ‼️\n` +
          `Component: ${componentName}\n` +
          `Total Memory Increase: ${totalIncrease.toFixed(1)}MB\n` +
          `Current Used Memory: ${readings[readings.length - 1]}MB\n` +
          `Initial Used Memory: ${readings[0]}MB\n` +
          `Memory Readings: ${readings.map((r) => r.toFixed(1)).join(" → ")}MB`
      );
    }

    return {
      isLeaking,
      memoryIncrease: totalIncrease,
    };
  },
}));
