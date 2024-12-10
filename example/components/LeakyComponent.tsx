import { withLeakDetection } from "memory-leak-mointor";
import React, { useEffect, useState } from "react";
import { View, Text, Button, StyleSheet } from "react-native";

const leakedData: any[] = [];

const BaseLeakyComponent = () => {
  const [counter, setCounter] = useState(0);

  useEffect(() => {
    const largeObject = new Array(10000).fill("🐛").map((item, index) => ({
      id: index,
      value: item,
      timestamp: Date.now(),
      data: new Array(1000).fill(Math.random().toString()),
    }));

    leakedData.push(largeObject);

    // Log the memory being leaked
    console.warn(
      `LeakyComponent leaked ${((leakedData.length * 8 * 1000) / 1024).toFixed(
        2
      )}MB of memory`
    );
  }, [counter]);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Memory Leak Demonstration</Text>
      <Text style={styles.info}>
        Leaked Objects: {leakedData.length}
        {"\n"}
        Approximate Memory: {((leakedData.length * 8 * 1000) / 1024).toFixed(
          2
        )}{" "}
        MB
      </Text>
      <Button
        title="Create Memory Leak"
        onPress={() => setCounter((prev) => prev + 1)}
      />
    </View>
  );
};

// Wrap the component with our leak detection HOC
export const LeakyComponent = withLeakDetection(
  BaseLeakyComponent,
  "LeakyComponent"
);

// Keep your existing styles...
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
