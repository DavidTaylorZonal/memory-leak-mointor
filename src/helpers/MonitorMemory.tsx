import React, { useEffect } from "react";
import { useLeakDetectionStore } from "../hooks/useLeakDetectionStore";
import MemoryLeakMonitor from "../MemoryLeakMointorModule";

interface MonitorMemoryProps {
  children: React.ReactNode;
  interval?: number;
}

export const MonitorMemory: React.FC<MonitorMemoryProps> = ({
  children,
  interval = 1000,
}) => {
  const subscribeToMemoryUpdates = useLeakDetectionStore(
    (state) => state.subscribeToMemoryUpdates
  );

  useEffect(() => {
    let unsubscribe: (() => void) | null = null;

    const initializeMonitoring = async () => {
      try {
        // Start the native module monitoring
        await MemoryLeakMonitor.startMemoryMonitoring(interval);

        // Subscribe to updates but don't log them
        unsubscribe = subscribeToMemoryUpdates();
      } catch (error) {
        console.error("Failed to initialize memory monitoring:", error);
      }
    };

    initializeMonitoring();

    return () => {
      if (unsubscribe) {
        unsubscribe();
      }
      MemoryLeakMonitor.stopMemoryMonitoring().catch(console.error);
    };
  }, [interval, subscribeToMemoryUpdates]);

  return <>{children}</>;
};
