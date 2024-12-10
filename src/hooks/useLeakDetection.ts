import { useEffect, useMemo } from "react";

import { useLeakDetectionStore } from "./useLeakDetectionStore";

// export function useLeakDetection(componentName: string) {
//   const { memoryInfo, trackComponent, getLeakStatus } = useLeakDetectionStore();

//   useEffect(() => {
//     if (memoryInfo?.appMemory) {
//       trackComponent(componentName, memoryInfo.appMemory);
//     }
//   }, [memoryInfo?.appMemory, componentName]);

//   return useMemo(
//     () => getLeakStatus(componentName),
//     [getLeakStatus, componentName]
//   );
// }
