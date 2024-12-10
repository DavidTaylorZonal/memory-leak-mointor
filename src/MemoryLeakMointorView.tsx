import { requireNativeView } from 'expo';
import * as React from 'react';

import { MemoryLeakMointorViewProps } from './MemoryLeakMointor.types';

const NativeView: React.ComponentType<MemoryLeakMointorViewProps> =
  requireNativeView('MemoryLeakMointor');

export default function MemoryLeakMointorView(props: MemoryLeakMointorViewProps) {
  return <NativeView {...props} />;
}
