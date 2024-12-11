import { useEffect } from "react";

import MemoryLeakMonitor from "../MemoryLeakMointorModule";

export function withLeakDetection<P extends object>(
  WrappedComponent: React.ComponentType<P>,
  componentName: string
) {
  const WithLeakDetectionComponent: React.FC<P> = (props: P) => {
    useEffect(() => {
      // Start tracking this component
      MemoryLeakMonitor.startComponentTracking(componentName).catch(
        console.error
      );

      return () => {
        // Stop tracking when component unmounts
        MemoryLeakMonitor.stopComponentTracking(componentName).catch(
          console.error
        );
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
