// Reexport the native module. On web, it will be resolved to MemoryLeakMointorModule.web.ts
// and on native platforms to MemoryLeakMointorModule.ts
export { default } from "./MemoryLeakMointorModule";
export * from "./MemoryLeakMointor.types";
export * from "./helpers/withLeakDetection";
export * from "./helpers/MonitorMemory";
