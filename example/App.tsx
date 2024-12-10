import { NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { MonitorMemory } from "memory-leak-mointor";
import React, { useEffect } from "react";

// import HomeScreen from "./screens/HomeScreen";
// import SecondScreen from "./screens/SecondScreen";
import { HomeScreen } from "./screens/HomeScreen";
import { LeakScreen } from "./screens/LeakyComponent";
import SecondScreen from "./screens/SecondScreen";

const Stack = createNativeStackNavigator();

export default function App() {
  return (
    <MonitorMemory>
      <NavigationContainer>
        <Stack.Navigator>
          <Stack.Screen
            name="Home"
            component={HomeScreen}
            options={{ title: "Memory Leak Demo" }}
          />
          <Stack.Screen
            name="Second"
            component={SecondScreen}
            options={{ title: "Second Screen" }}
          />
          <Stack.Screen
            name="Leak"
            component={LeakScreen}
            options={{ title: "Leaky Component" }}
          />
        </Stack.Navigator>
      </NavigationContainer>
    </MonitorMemory>
  );
}
