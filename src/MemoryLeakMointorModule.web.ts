import { registerWebModule, NativeModule } from 'expo';

import { MemoryLeakMointorModuleEvents } from './MemoryLeakMointor.types';

class MemoryLeakMointorModule extends NativeModule<MemoryLeakMointorModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
}

export default registerWebModule(MemoryLeakMointorModule);
