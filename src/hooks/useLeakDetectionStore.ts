import { create } from "zustand";

import type { MemoryInfo } from "../MemoryLeakMointor.types";
import MemoryLeakMonitor from "../MemoryLeakMointorModule";

interface LeakStatus {
  name: string;
  isLeaking: boolean;
  memoryIncrease: number;
}

interface SnapshotData {
  memoryUsage: number;
  timestamp: number;
  increases: number;
}

interface LeakDetectionState {
  memoryInfo: MemoryInfo | null;
  componentSnapshots: Record<string, SnapshotData>;
  cachedLeakStatuses: LeakStatus[];
  subscribeToMemoryUpdates: () => () => void;
  trackComponent: (componentName: string, currentMemory: number) => void;
  getLeakStatus: (componentName: string) => {
    isLeaking: boolean;
    memoryIncrease: number;
  };
}

export const useLeakDetectionStore = create<LeakDetectionState>((set, get) => ({
  memoryInfo: null,
  componentSnapshots: {},
  cachedLeakStatuses: [],

  subscribeToMemoryUpdates: () => {
    const subscription = MemoryLeakMonitor.addListener(
      "onMemoryUpdate",
      (event: { memoryInfo: MemoryInfo }) => {
        set({ memoryInfo: event.memoryInfo });
      }
    );

    return () => subscription.remove();
  },

  trackComponent: (componentName: string, currentMemory: number) => {
    const { componentSnapshots } = get();
    const snapshot = componentSnapshots[componentName];
    const now = Date.now();

    if (!snapshot) {
      set((state) => ({
        componentSnapshots: {
          ...state.componentSnapshots,
          [componentName]: {
            memoryUsage: currentMemory,
            timestamp: now,
            increases: 0,
          },
        },
      }));
      return;
    }

    if (currentMemory > snapshot.memoryUsage) {
      set((state) => {
        const newSnapshots = {
          ...state.componentSnapshots,
          [componentName]: {
            memoryUsage: currentMemory,
            timestamp: now,
            increases: snapshot.increases + 1,
          },
        };

        const newStatuses = Object.entries(newSnapshots).map(
          ([name, data]) => ({
            name,
            isLeaking: data.increases >= 3,
            memoryIncrease: data.memoryUsage,
          })
        );

        return {
          componentSnapshots: newSnapshots,
          cachedLeakStatuses: newStatuses,
        };
      });
    }
  },

  getLeakStatus: (componentName: string) => {
    const { componentSnapshots } = get();
    const snapshot = componentSnapshots[componentName];

    if (!snapshot) {
      return { isLeaking: false, memoryIncrease: 0 };
    }

    return {
      isLeaking: snapshot.increases >= 3,
      memoryIncrease: snapshot.memoryUsage,
    };
  },
}));
