import React from 'react';
import {Text, View, TurboModuleRegistry} from 'react-native';
const native = TurboModuleRegistry.getEnforcing('NativeAppForgeRust');
export default function App() {
 const answer = native.answer();
 if (answer !== 42) throw new Error('Rust TurboModule result mismatch');
 return <View><Text>Rust TurboModule: {answer}</Text></View>;
}
