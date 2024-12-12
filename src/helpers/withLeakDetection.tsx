import { useEffect, useRef } from "react";

import MemoryLeakMonitor from "../MemoryLeakMointorModule";

export function withLeakDetection<P extends object>(
  WrappedComponent: React.ComponentType<P>,
  componentName: string
) {
  const WithLeakDetectionComponent: React.FC<P> = (props: P) => {
    // Store the componentId for cleanup
    const componentIdRef = useRef<string>();

    useEffect(() => {
      // Start tracking this component and store the returned ID
      MemoryLeakMonitor.startComponentTracking(componentName)
        .then((id) => {
          componentIdRef.current = id;
        })
        .catch((error) => {
          console.error(`Failed to start tracking ${componentName}:`, error);
        });

      return () => {
        // Use the stored componentId for cleanup
        if (componentIdRef.current) {
          MemoryLeakMonitor.stopComponentTracking(componentIdRef.current).catch(
            (error) => {
              console.error(`Failed to stop tracking ${componentName}:`, error);
            }
          );
        }
      };
    }, []);

    return <WrappedComponent {...props} />;
  };

  WithLeakDetectionComponent.displayName = `WithLeakDetection(${
    componentName ||
    WrappedComponent.displayName ||
    WrappedComponent.name ||
    "Component"
  })`;

  return WithLeakDetectionComponent;
}
