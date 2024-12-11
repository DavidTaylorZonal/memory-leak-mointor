import React, { useEffect } from "react";

import MemoryLeakMonitor from "../MemoryLeakMointorModule";
import { useLeakDetectionStore } from "../hooks/useLeakDetectionStore";

interface MonitorMemoryProps {
  children: React.ReactNode;
  interval?: number;
}

export const MonitorMemory: React.FC<MonitorMemoryProps> = ({
  children,
  interval = 1000,
}) => {
  const initializeMonitoring = async () => {
    try {
      await MemoryLeakMonitor.startMemoryMonitoring(interval);
      // Set up listeners for memory updates and leak detection
      const memorySubscription = MemoryLeakMonitor.addListener(
        "onMemoryUpdate",
        (event) => {
          // Just update the store with latest memory info
          useLeakDetectionStore.setState({ memoryInfo: event.memoryInfo });
        }
      );

      return () => {
        memorySubscription.remove();
        MemoryLeakMonitor.stopMemoryMonitoring().catch(console.error);
      };
    } catch (error) {
      console.error("Failed to initialize memory monitoring:", error);
    }
    return () => null;
  };

  useEffect(() => {
    initializeMonitoring();
  }, [interval]);

  return <>{children}</>;
};
