import React, { useEffect } from "react";

import MemoryLeakMonitor from "../MemoryLeakMointorModule";

interface MonitorMemoryProps {
  children: React.ReactNode;
  interval?: number;
  sessionDurationMinutes?: number;
}

export const MonitorMemory: React.FC<MonitorMemoryProps> = ({
  children,
  interval = 1000,
  sessionDurationMinutes = 0.5,
}) => {
  useEffect(() => {
    let timeoutId: number;

    const initializeMonitoring = async () => {
      try {
        // Start memory monitoring
        await MemoryLeakMonitor.startMemoryMonitoring(interval);

        // Start the session
        await MemoryLeakMonitor.startSession(sessionDurationMinutes);
        console.log(
          `MONITOR: Started ${sessionDurationMinutes} minute session`
        );

        // Force stop session after duration
        const timeoutMs = sessionDurationMinutes * 60 * 1000;
        timeoutId = window.setTimeout(async () => {
          try {
            console.log("MONITOR: Session timeout reached, stopping session");
            await MemoryLeakMonitor.stopSession();
          } catch (error) {
            console.error("Failed to stop session:", error);
          }
        }, timeoutMs);

        return () => {
          console.log("MONITOR: Cleaning up monitoring");
          window.clearTimeout(timeoutId);

          // Clean up in sequence
          const cleanup = async () => {
            try {
              await MemoryLeakMonitor.stopSession();
              await MemoryLeakMonitor.stopMemoryMonitoring();
            } catch (error) {
              console.error("Cleanup failed:", error);
            }
          };
          cleanup();
        };
      } catch (error) {
        console.error("Failed to initialize monitoring:", error);
        return () => null;
      }
    };

    const cleanup = initializeMonitoring();
    return () => {
      cleanup.then((cleanupFn) => cleanupFn?.());
    };
  }, [interval, sessionDurationMinutes]);

  return <>{children}</>;
};
