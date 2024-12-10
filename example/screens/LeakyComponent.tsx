import { useLeakDetectionStore, withLeakDetection } from "memory-leak-mointor";
import React, { useEffect, useState } from "react";
import { View, Text, Button, StyleSheet } from "react-native";

// Create large objects that won't be cleaned up
const leakedData: any[] = [];

const BaseLeakyComponent = () => {
  const [counter, setCounter] = useState(0);

  const memoryused = useLeakDetectionStore((state) => state.memoryInfo);

  useEffect(() => {
    // Create memory leak by storing large objects without cleanup
    const largeObject = new Array(10000).fill("🐛").map((item, index) => ({
      id: index,
      value: item,
      timestamp: Date.now(),
      data: new Array(1000).fill(Math.random().toString()),
    }));

    leakedData.push(largeObject);

    // Not manually logging - letting native module detect the leak
    return () => {
      // Deliberately not cleaning up to cause the leak
    };
  }, [counter]);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Memory Leak Demonstration</Text>
      <Text style={styles.info}>
        Number of leaked objects: {leakedData.length}
      </Text>
      <Text style={styles.info}>memeory used: {memoryused?.usedMemory} MB</Text>
      <Text style={styles.info}>
        memeory available: {memoryused?.availableMemory} MB
      </Text>
      <Button
        title="Create Memory Leak"
        onPress={() => setCounter((prev) => prev + 1)}
      />
    </View>
  );
};

export const LeakScreen = withLeakDetection(
  BaseLeakyComponent,
  "LeakyComponent"
);

const styles = StyleSheet.create({
  container: {
    padding: 20,
    backgroundColor: "#fff",
    borderRadius: 8,
    margin: 10,
  },
  title: {
    fontSize: 18,
    fontWeight: "bold",
    marginBottom: 10,
  },
  info: {
    marginVertical: 10,
    color: "#666",
  },
  warning: {
    marginTop: 10,
    color: "red",
    fontSize: 12,
  },
});
