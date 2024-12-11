// useLeakDetectionStore.ts
import { create } from "zustand";
import type { MemoryInfo } from "../MemoryLeakMointor.types";
import MemoryLeakMonitor from "../MemoryLeakMointorModule";

interface LeakDetectionState {
  memoryInfo: MemoryInfo | null;
  leaks: Record<string, any>; // Store detected leaks
  subscribeToMemoryUpdates: () => () => void;
  startTracking: (componentName: string) => Promise<void>;
  stopTracking: (componentName: string) => Promise<void>;
}

export const useLeakDetectionStore = create<LeakDetectionState>((set, get) => ({
  memoryInfo: null,
  leaks: {},

  subscribeToMemoryUpdates: () => {
    // Subscribe to memory updates
    const memorySubscription = MemoryLeakMonitor.addListener(
      "onMemoryUpdate",
      (event: { memoryInfo: MemoryInfo }) => {
        set({ memoryInfo: event.memoryInfo });
      }
    );

    // Subscribe to leak detection events
    const leakSubscription = MemoryLeakMonitor.addListener(
      "onLeakDetected",
      (leakInfo) => {
        console.warn("🚨 Memory leak detected:", leakInfo);
        set((state) => ({
          leaks: {
            ...state.leaks,
            [leakInfo.componentName]: leakInfo,
          },
        }));
      }
    );

    // Return cleanup function
    return () => {
      memorySubscription.remove();
      leakSubscription.remove();
    };
  },

  startTracking: async (componentName: string) => {
    try {
      await MemoryLeakMonitor.startComponentTracking(componentName);
      console.log(`Started tracking ${componentName}`);
    } catch (error) {
      console.error(`Failed to start tracking ${componentName}:`, error);
    }
  },

  stopTracking: async (componentName: string) => {
    try {
      await MemoryLeakMonitor.stopComponentTracking(componentName);
      console.log(`Stopped tracking ${componentName}`);
    } catch (error) {
      console.error(`Failed to stop tracking ${componentName}:`, error);
    }
  },
}));
