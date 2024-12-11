# React Native Memory Leak Monitor 🔍

🔥 A powerful memory leak detection tool for React Native, inspired by LeakCanary! Monitor and catch memory leaks in real-time.

> ⚠️ **Currently Android Only** (iOS coming soon! 🍎)

## ✨ Features

🔄 Real-time memory usage monitoring  
🎯 Component-level leak detection  
⏱️ Session-based memory analysis  
📊 Zustand store integration  
📱 Memory metrics tracking  
🚨 Automatic leak notifications  
🎁 Easy-to-use HOC wrapper

## 🚀 Installation

```bash
npm install memory-leak-monitor
# or
yarn add memory-leak-monitor
```

### 🤖 Android Setup

Add to `android/app/build.gradle`:

```gradle
dependencies {
    // ... other dependencies
    implementation project(':memory-leak-monitor')
}
```

## 📖 Basic Usage

### 1. 🌍 Wrap Your App

```tsx
import { MonitorMemory } from "memory-leak-monitor";

function App() {
  return (
    <MonitorMemory
      interval={1000} // Update interval in ms
      sessionDurationMinutes={0.5} // Session duration
    >
      {/* Your app content */}
    </MonitorMemory>
  );
}
```

### 2. 🎯 Monitor Components

```tsx
import { withLeakDetection } from "memory-leak-monitor";

const MyComponent = () => {
  // Your component code
};

export default withLeakDetection(MyComponent, "MyComponent");
```

### 3. 📊 Track Memory Stats

```tsx
import { useLeakDetectionStore } from "memory-leak-monitor";

function MemoryDisplay() {
  const memoryInfo = useLeakDetectionStore((state) => state.memoryInfo);

  return (
    <View>
      <Text>Used Memory: {memoryInfo?.usedMemory} MB</Text>
      <Text>Available: {memoryInfo?.availableMemory} MB</Text>
    </View>
  );
}
```

## 🛠️ API Reference

### 🌟 MonitorMemory Component

```tsx
<MonitorMemory
  interval?: number;          // Memory check interval (ms)
  sessionDurationMinutes?: number;  // Monitoring session duration
>
```

### 🎨 withLeakDetection HOC

```tsx
withLeakDetection(
  WrappedComponent: React.ComponentType,
  componentName: string
)
```

### 🪝 useLeakDetectionStore Hook

```tsx
const {
  memoryInfo, // Current memory metrics
  leaks, // Detected memory leaks
  startTracking, // Start tracking a component
  stopTracking, // Stop tracking a component
  subscribeToMemoryUpdates, // Subscribe to memory updates
} = useLeakDetectionStore();
```

### 📋 Memory Info Type

```typescript
type MemoryInfo = {
  totalMemory: number; // Total device memory (MB)
  availableMemory: number; // Available memory (MB)
  usedMemory: number; // Used memory (MB)
  appMemory: number; // App's memory usage (MB)
  isLowMemory: boolean; // Low memory warning
  lowMemoryThreshold: number; // Low memory threshold (MB)
};
```

## 🎯 Detection Settings

Default thresholds for leak detection:

📊 Minimum memory change: `5MB`  
📈 Significant increase: `20MB`  
🔄 Sustained increase window: `8 readings`  
💾 System memory threshold: `50MB`

## 💡 Best Practices

1. 🏁 Start monitoring early in app lifecycle
2. 🎯 Monitor suspicious components first
3. ⏱️ Use sessions during development
4. 📝 Check memory after major UI changes
5. 🔄 Monitor component mount/unmount cycles

## ⚠️ Limitations

- 🤖 Android only (iOS coming soon!)
- 📱 Readings vary between devices
- 🔄 Background processes affect metrics
- 🗑️ Garbage collection impacts results

## 🤝 Contributing

We love contributions! Check out our [Contributing Guide](CONTRIBUTING.md) for guidelines.

## 📄 License

MIT

## 💪 Support

Found a bug? Have a feature request? [Open an issue](https://github.com/yourusername/memory-leak-monitor/issues)!

## 🌟 Show Your Support

Give a ⭐️ if this project helped you!

---
