import * as React from 'react';

import { MemoryLeakMointorViewProps } from './MemoryLeakMointor.types';

export default function MemoryLeakMointorView(props: MemoryLeakMointorViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
