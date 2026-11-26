import React from 'react';
import {NativeModules, Text, View} from 'react-native';
export default function App() {
  if (!NativeModules.AppForgeRustJsi?.install()) throw new Error('Rust JSI bridge unavailable');
  const answer = global.appforgeRustAnswer();
  if (answer !== 42) throw new Error('Rust JSI answer mismatch');
  return <View><Text>Rust JSI: {answer}</Text></View>;
}
