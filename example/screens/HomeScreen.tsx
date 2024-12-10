import { useNavigation } from "@react-navigation/native";
import { withLeakDetection } from "memory-leak-mointor";
import React from "react";
import { View, Text, Button, StyleSheet } from "react-native";

function BaseHomeScreen() {
  const navigation = useNavigation();

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Memory Leak Detection Demo</Text>
      <Text style={styles.description}>
        Navigate through the screens to test memory leak detection. The leaky
        component is on the third screen.
      </Text>
      <Button
        title="Go to Second Screen"
        onPress={() => navigation.navigate("Second")}
      />
    </View>
  );
}

export const HomeScreen = withLeakDetection(BaseHomeScreen, "BaseHomeScreen");

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#fff",
    padding: 20,
  },
  scrollView: {
    flex: 1,
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
