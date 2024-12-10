import { NativeModule, requireNativeModule } from 'expo';

import { MemoryLeakMointorModuleEvents } from './MemoryLeakMointor.types';

declare class MemoryLeakMointorModule extends NativeModule<MemoryLeakMointorModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<MemoryLeakMointorModule>('MemoryLeakMointor');
