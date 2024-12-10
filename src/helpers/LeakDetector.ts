import { MemoryInfo } from "../MemoryLeakMointor.types";

// Interface for component memory tracking
interface ComponentMemorySnapshot {
  componentName: string;
  timestamp: number;
  memoryUsage: number;
  instanceId: string;
}

interface LeakReport {
  componentName: string;
  instanceId: string;
  memoryIncrease: number;
  timeElapsed: number;
}

class LeakDetector {
  private static instance: LeakDetector;
  private memorySnapshots: ComponentMemorySnapshot[] = [];
  private readonly LEAK_THRESHOLD_MB = 10; // Consider it a leak if memory increases by 10MB
  private readonly TIME_WINDOW_MS = 5000; // Time window to analyze memory growth
  private onLeakDetected: ((report: LeakReport) => void) | null = null;

  private constructor() {}

  static getInstance(): LeakDetector {
    if (!LeakDetector.instance) {
      LeakDetector.instance = new LeakDetector();
    }
    return LeakDetector.instance;
  }

  setLeakCallback(callback: (report: LeakReport) => void) {
    this.onLeakDetected = callback;
  }

  takeSnapshot(
    componentName: string,
    instanceId: string,
    memoryInfo: MemoryInfo
  ) {
    const snapshot: ComponentMemorySnapshot = {
      componentName,
      instanceId,
      timestamp: Date.now(),
      memoryUsage: memoryInfo.appMemory,
    };

    this.memorySnapshots.push(snapshot);
    this.analyzeMemoryGrowth(componentName, instanceId);
  }

  private analyzeMemoryGrowth(componentName: string, instanceId: string) {
    const now = Date.now();
    const relevantSnapshots = this.memorySnapshots
      .filter(
        (snapshot) =>
          snapshot.componentName === componentName &&
          snapshot.instanceId === instanceId &&
          snapshot.timestamp >= now - this.TIME_WINDOW_MS
      )
      .sort((a, b) => a.timestamp - b.timestamp);

    if (relevantSnapshots.length < 2) return;

    const oldestSnapshot = relevantSnapshots[0];
    const latestSnapshot = relevantSnapshots[relevantSnapshots.length - 1];
    const memoryIncrease =
      latestSnapshot.memoryUsage - oldestSnapshot.memoryUsage;
    const timeElapsed = latestSnapshot.timestamp - oldestSnapshot.timestamp;

    if (memoryIncrease >= this.LEAK_THRESHOLD_MB) {
      const report: LeakReport = {
        componentName,
        instanceId,
        memoryIncrease,
        timeElapsed,
      };

      if (this.onLeakDetected) {
        this.onLeakDetected(report);
      }
    }

    // Clean up old snapshots
    this.memorySnapshots = this.memorySnapshots.filter(
      (snapshot) => snapshot.timestamp >= now - this.TIME_WINDOW_MS
    );
  }
}

export default LeakDetector;
