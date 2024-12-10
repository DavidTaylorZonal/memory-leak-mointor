import { EventSubscription } from "expo-modules-core";
import MemoryLeakMonitor, { useLeakDetectionStore } from "memory-leak-mointor";
import type { MemoryInfo, MemoryUpdateEventPayload } from "memory-leak-mointor";
import React, { useEffect, useState } from "react";
import {
  Button,
  SafeAreaView,
  ScrollView,
  Text,
  View,
  StyleSheet,
} from "react-native";

import { LeakyComponent } from "./components/LeakyComponent";

export default function App() {
  const [memoryInfo, setMemoryInfo] = useState<MemoryInfo | null>(null);
  const [isMonitoring, setIsMonitoring] = useState(false);

  const subscribeToMemoryUpdates = useLeakDetectionStore(
    (state) => state.subscribeToMemoryUpdates
  );

  useEffect(() => {
    let subscription: EventSubscription | null = null;

    const startMonitoring = async () => {
      try {
        await MemoryLeakMonitor.startMemoryMonitoring(1000);
        setIsMonitoring(true);

        subscription = MemoryLeakMonitor.addListener(
          "onMemoryUpdate",
          (event: MemoryUpdateEventPayload) => {
            setMemoryInfo(event.memoryInfo);
          }
        );
      } catch (error) {
        console.error("Failed to start monitoring:", error);
        setIsMonitoring(false);
      }
    };

    startMonitoring();
    const unsubscribeLeakDetection = subscribeToMemoryUpdates();

    return () => {
      subscription?.remove();
      unsubscribeLeakDetection();
      MemoryLeakMonitor.stopMemoryMonitoring()
        .then(() => setIsMonitoring(false))
        .catch((error) => {
          console.error("Failed to stop monitoring:", error);
        });
    };
  }, []);

  const handleStartMonitoring = async () => {
    try {
      await MemoryLeakMonitor.startMemoryMonitoring(1000);
      setIsMonitoring(true);
    } catch (error) {
      console.error("Failed to start monitoring:", error);
      setIsMonitoring(false);
    }
  };

  const handleStopMonitoring = async () => {
    try {
      await MemoryLeakMonitor.stopMemoryMonitoring();
      setIsMonitoring(false);
    } catch (error) {
      console.error("Failed to stop monitoring:", error);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Memory Monitor</Text>
        <Group name="Memory Stats">
          {memoryInfo ? (
            <>
              <Text style={styles.statLine}>
                App Memory: {memoryInfo.appMemory} MB
              </Text>
              <Text style={styles.statLine}>
                Available Memory: {memoryInfo.availableMemory} MB
              </Text>
              <Text style={styles.statLine}>
                Total Memory: {memoryInfo.totalMemory} MB
              </Text>
              <Text style={styles.statLine}>
                Used Memory: {memoryInfo.usedMemory} MB
              </Text>
              <Text style={styles.statLine}>
                Low Memory Threshold: {memoryInfo.lowMemoryThreshold} MB
              </Text>
              {memoryInfo.isLowMemory && (
                <Text style={styles.warningText}>Low Memory Warning!</Text>
              )}
            </>
          ) : (
            <Text>Loading memory info...</Text>
          )}
        </Group>

        <LeakyComponent />

        <Group name="Memory Monitor Controls">
          <View style={styles.buttonContainer}>
            <Button
              title={isMonitoring ? "Monitoring Active" : "Start Monitoring"}
              onPress={handleStartMonitoring}
              disabled={isMonitoring}
            />
            <Button
              title="Stop Monitoring"
              onPress={handleStopMonitoring}
              disabled={!isMonitoring}
            />
          </View>
        </Group>
      </ScrollView>
    </SafeAreaView>
  );
}

interface GroupProps {
  name: string;
  children: React.ReactNode;
}

function Group({ name, children }: GroupProps) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupHeader}>{name}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#eee",
  },
  header: {
    fontSize: 30,
    margin: 20,
    fontWeight: "600",
  },
  groupHeader: {
    fontSize: 20,
    marginBottom: 20,
    fontWeight: "500",
  },
  group: {
    margin: 20,
    backgroundColor: "#fff",
    borderRadius: 10,
    padding: 20,
    shadowColor: "#000",
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  buttonContainer: {
    gap: 10,
  },
  warningText: {
    color: "red",
    fontWeight: "bold",
    marginTop: 10,
  },
  statLine: {
    fontSize: 16,
    marginVertical: 4,
  },
});
