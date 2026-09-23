import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function read(relative) {
  return fs.readFileSync(new URL(`../../${relative}`, import.meta.url), "utf8");
}

const route = read(
  "android-app/app/src/main/java/com/appforge/studio/UnifiedAgentStudioRoute.kt"
);
const assistant = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeLocalAssistant.kt"
);

test("active session is hidden from recent history", () => {
  assert.match(
    route,
    /it\.sessionId\s*!=\s*currentSessionId\s*&&\s*it\.sessionId\s*!=\s*pendingSession/s
  );
});

test("structured AI owns the single LiteRTLM conversation", () => {
  const a = assistant.indexOf("suspend fun generateStructuredJson");
  const b = assistant.indexOf("suspend fun resetConversation");
  const method = assistant.slice(a, b);

  const close = method.indexOf("conversation?.close()");
  const clear = method.indexOf("conversation = null");
  const create = method.indexOf("currentEngine.createConversation");

  assert.ok(close >= 0);
  assert.ok(clear > close);
  assert.ok(create > clear);
  assert.match(
    method,
    /finally\s*\{[\s\S]*structuredConversation\.close\(\)[\s\S]*conversation\s*=\s*runCatching[\s\S]*conversationConfig\(\)/s
  );
});

test("raw LiteRTLM collision gets a Turkish UI fallback", () => {
  assert.match(route, /FAILED_PRECONDITION/);
  assert.match(route, /Yerel AI oturumu çakıştı/);
});
