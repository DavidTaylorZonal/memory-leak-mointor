// Reexport the native module. On web, it will be resolved to MemoryLeakMointorModule.web.ts
// and on native platforms to MemoryLeakMointorModule.ts
export { default } from './MemoryLeakMointorModule';
export { default as MemoryLeakMointorView } from './MemoryLeakMointorView';
export * from  './MemoryLeakMointor.types';
