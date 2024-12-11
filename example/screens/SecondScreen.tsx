import { useNavigation } from "@react-navigation/native";
import { withLeakDetection } from "memory-leak-mointor";
import React, { useEffect } from "react";
import { View, Text, Button, StyleSheet } from "react-native";

// Create large objects that won't be cleaned up
// const leakedData: any[] = [];

// const createLeakObject = () => {
//   return new Array(10000).fill("🐛").map((item, index) => ({
//     id: index,
//     value: item,
//     timestamp: Date.now(),
//     data: new Array(1000).fill(Math.random().toString()),
//   }));
// };

export default function BaseSecondScreen() {
  const navigation = useNavigation();

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Second Screen</Text>
      <Text style={styles.description}>
        This is an intermediate screen. The next screen contains the component
        that will demonstrate memory leaks.
      </Text>
      <Button
        title="Go to Leaky Component"
        onPress={() => navigation.navigate("Leak")}
      />
    </View>
  );
}

// The withLeakDetection HOC will use the native module to detect leaks
export const SecondScreen = withLeakDetection(
  BaseSecondScreen,
  "BaseSecondScreen"
);

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#fff",
    padding: 20,
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 20,
  },
  description: {
    fontSize: 16,
    color: "#666",
    marginBottom: 30,
    lineHeight: 22,
  },
});
