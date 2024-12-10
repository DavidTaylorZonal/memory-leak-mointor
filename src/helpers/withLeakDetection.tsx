import { useEffect } from "react";

import { useLeakDetection } from "../hooks/useLeakDetection";

export function withLeakDetection<P extends object>(
  WrappedComponent: React.ComponentType<P>,
  componentName: string
) {
  const WithLeakDetectionComponent: React.FC<P> = (props: P) => {
    const leakStatus = useLeakDetection(componentName);

    useEffect(() => {
      if (leakStatus.isLeaking) {
        console.warn(
          `Memory leak detected in ${componentName}. ` +
            `Increase: ${leakStatus.memoryIncrease}MB`
        );
      }
    }, [leakStatus]);

    return <WrappedComponent {...props} />;
  };

  WithLeakDetectionComponent.displayName = `WithLeakDetection(${componentName || WrappedComponent.displayName || WrappedComponent.name || "Component"})`;

  return WithLeakDetectionComponent;
}
