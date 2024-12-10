import { useEffect, useRef, useState } from "react";
import { useLeakDetectionStore } from "../hooks/useLeakDetectionStore";

export function withLeakDetection<P extends object>(
  WrappedComponent: React.ComponentType<P>,
  componentName: string
) {
  const WithLeakDetectionComponent: React.FC<P> = (props: P) => {
    const { memoryInfo, trackComponent, getLeakStatus } =
      useLeakDetectionStore();
    const hasWarnedRef = useRef(false);

    // // Track memory changes
    // useEffect(() => {
    //   if (memoryInfo?.usedMemory) {
    //     console.log("We are rendering here");
    //     trackComponent(componentName, memoryInfo.usedMemory);
    //     const newStatus = getLeakStatus(componentName);

    //     if (newStatus.isLeaking && !hasWarnedRef.current) {
    //       hasWarnedRef.current = true;
    //     }
    //   }
    // }, [memoryInfo?.usedMemory, componentName]);

    // useEffect(() => {
    //   return () => {
    //     hasWarnedRef.current = false;
    //   };
    // }, []);

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
