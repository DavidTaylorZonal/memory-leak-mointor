import { requireNativeModule } from "expo-modules-core";

import { MemoryLeakMonitorModule } from "./MemoryLeakMointor.types";

// This loads the native module object from the JSI
export default requireNativeModule<MemoryLeakMonitorModule>(
  "MemoryLeakMointor"
);
