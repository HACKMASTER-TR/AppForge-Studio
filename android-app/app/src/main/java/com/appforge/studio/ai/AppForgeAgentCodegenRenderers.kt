package com.appforge.studio.ai

internal object AppForgeAgentAndroidRenderer {
    fun render(blueprint: AppForgeAgentBlueprint): AppForgeRendererOutput {
        val packageName = "com.appforge.generated.${safeIdentifier(blueprint.appName).lowercase()}"
        val packagePath = packageName.replace('.', '/')
        val mainPath = "android/app/src/main/java/$packagePath/MainActivity.kt"

        val screenFunctions = blueprint.screens.joinToString("\n\n") { screen ->
            val functionName = "Screen${pascalIdentifier(screen.id)}"
            val components = if (screen.components.isEmpty()) {
                "            Text(\"${kotlinString(screen.purpose)}\")"
            } else {
                screen.components.joinToString("\n") { component ->
                    "            Text(\"• ${kotlinString(component)}\")"
                }
            }
            val actions = screen.actions.joinToString("\n") { action ->
                val target = action.targetRoute?.let(::kotlinString)
                if (target != null) {
                    "            Button(onClick = { onNavigate(\"$target\") }) { Text(\"${kotlinString(action.label)}\") }"
                } else {
                    "            Button(onClick = { }) { Text(\"${kotlinString(action.label)}\") }"
                }
            }

            """
            @Composable
            private fun $functionName(onNavigate: (String) -> Unit) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(${blueprint.tokens.spacingUnitDp * 2}.dp),
                    verticalArrangement = Arrangement.spacedBy(${blueprint.tokens.spacingUnitDp}.dp)
                ) {
                    Text(
                        text = "${kotlinString(screen.title)}",
                        style = MaterialTheme.typography.headlineMedium
                    )
$components
$actions
                }
            }
            """.trimIndent()
        }

        val routeCases = blueprint.screens.joinToString("\n") { screen ->
            "                \"${kotlinString(screen.route)}\" -> Screen${pascalIdentifier(screen.id)} { route = it }"
        }

        val main = """
            package $packageName

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.Arrangement
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.Button
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Surface
            import androidx.compose.material3.Text
            import androidx.compose.material3.lightColorScheme
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.getValue
            import androidx.compose.runtime.mutableStateOf
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.setValue
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.unit.dp

            private val AppForgeColors = lightColorScheme(
                primary = Color(${androidColor(blueprint.tokens.primary)}),
                secondary = Color(${androidColor(blueprint.tokens.secondary)}),
                background = Color(${androidColor(blueprint.tokens.background)}),
                surface = Color(${androidColor(blueprint.tokens.surface)}),
                onBackground = Color(${androidColor(blueprint.tokens.text)}),
                onSurface = Color(${androidColor(blueprint.tokens.text)})
            )

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent { AppForgeGeneratedApp() }
                }
            }

            @Composable
            private fun AppForgeGeneratedApp() {
                var route by remember { mutableStateOf("${kotlinString(blueprint.startRoute)}") }
                MaterialTheme(colorScheme = AppForgeColors) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        when (route) {
$routeCases
                            else -> route = "${kotlinString(blueprint.startRoute)}"
                        }
                    }
                }
            }

            $screenFunctions
        """.trimIndent()

        val manifest = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:label="${xmlEscape(blueprint.appName)}"
                    android:theme="@style/Theme.Material3.DayNight.NoActionBar">
                    <activity
                        android:name="$packageName.MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent()

        return AppForgeRendererOutput(
            entryPoint = mainPath,
            files = listOf(
                AppForgeGeneratedFile(mainPath, main),
                AppForgeGeneratedFile("android/app/src/main/AndroidManifest.xml", manifest)
            )
        )
    }
}

internal object AppForgeAgentFlutterRenderer {
    fun render(blueprint: AppForgeAgentBlueprint): AppForgeRendererOutput {
        val routeEntries = blueprint.screens.joinToString(",\n") { screen ->
            "        '${dartString(screen.route)}': (_) => const ${pascalIdentifier(screen.id)}Screen()"
        }

        val screenClasses = blueprint.screens.joinToString("\n\n") { screen ->
            val componentWidgets = screen.components.joinToString(",\n") { component ->
                "              Text('• ${dartString(component)}')"
            }
            val actionWidgets = screen.actions.joinToString(",\n") { action ->
                val target = action.targetRoute
                if (target != null) {
                    "              ElevatedButton(onPressed: () => Navigator.pushNamed(context, '${dartString(target)}'), child: const Text('${dartString(action.label)}'))"
                } else {
                    "              ElevatedButton(onPressed: () {}, child: const Text('${dartString(action.label)}'))"
                }
            }
            val children = listOf(
                "              const Text('${dartString(screen.purpose)}')",
                componentWidgets,
                actionWidgets
            ).filter { it.isNotBlank() }.joinToString(",\n")

            """
            class ${pascalIdentifier(screen.id)}Screen extends StatelessWidget {
              const ${pascalIdentifier(screen.id)}Screen({super.key});

              @override
              Widget build(BuildContext context) {
                return Scaffold(
                  appBar: AppBar(title: const Text('${dartString(screen.title)}')),
                  body: Padding(
                    padding: const EdgeInsets.all(${blueprint.tokens.spacingUnitDp * 2}.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
$children
                      ],
                    ),
                  ),
                );
              }
            }
            """.trimIndent()
        }

        val main = """
            import 'package:flutter/material.dart';

            void main() => runApp(const AppForgeGeneratedApp());

            class AppForgeGeneratedApp extends StatelessWidget {
              const AppForgeGeneratedApp({super.key});

              @override
              Widget build(BuildContext context) {
                return MaterialApp(
                  title: '${dartString(blueprint.appName)}',
                  theme: ThemeData(
                    colorScheme: ColorScheme.fromSeed(
                      seedColor: const Color(${flutterColor(blueprint.tokens.primary)}),
                      surface: const Color(${flutterColor(blueprint.tokens.surface)}),
                    ),
                    scaffoldBackgroundColor: const Color(${flutterColor(blueprint.tokens.background)}),
                    useMaterial3: true,
                  ),
                  initialRoute: '${dartString(blueprint.startRoute)}',
                  routes: {
$routeEntries
                  },
                );
              }
            }

            $screenClasses
        """.trimIndent()

        return AppForgeRendererOutput(
            entryPoint = "flutter/lib/main.dart",
            files = listOf(AppForgeGeneratedFile("flutter/lib/main.dart", main))
        )
    }
}

internal object AppForgeAgentReactNativeRenderer {
    fun render(blueprint: AppForgeAgentBlueprint): AppForgeRendererOutput {
        val routeType = blueprint.screens.joinToString(" | ") { "'${jsString(it.route)}'" }
        val routeCases = blueprint.screens.joinToString("\n") { screen ->
            val lines = mutableListOf<String>()
            lines += "        <Text style={styles.title}>${jsxText(screen.title)}</Text>"
            lines += "        <Text style={styles.text}>${jsxText(screen.purpose)}</Text>"
            screen.components.forEach { component ->
                lines += "        <Text style={styles.text}>• ${jsxText(component)}</Text>"
            }
            screen.actions.forEach { action ->
                val target = action.targetRoute
                val handler = if (target != null) {
                    "() => setRoute('${jsString(target)}')"
                } else {
                    "() => undefined"
                }
                lines += "        <Button title=\"${jsxAttribute(action.label)}\" onPress={$handler} />"
            }
            """
              if (route === '${jsString(screen.route)}') {
                return (
                  <View style={styles.screen}>
${lines.joinToString("\n")}
                  </View>
                );
              }
            """.trimIndent()
        }

        val app = """
            import React, {useState} from 'react';
            import {Button, SafeAreaView, StyleSheet, Text, View} from 'react-native';

            type Route = $routeType;

            export default function App() {
              const [route, setRoute] = useState<Route>('${jsString(blueprint.startRoute)}');

$routeCases

              setRoute('${jsString(blueprint.startRoute)}');
              return <SafeAreaView style={styles.screen} />;
            }

            const styles = StyleSheet.create({
              screen: {
                flex: 1,
                padding: ${blueprint.tokens.spacingUnitDp * 2},
                gap: ${blueprint.tokens.spacingUnitDp},
                backgroundColor: '${blueprint.tokens.background}',
              },
              title: {
                fontSize: 28,
                fontWeight: '700',
                color: '${blueprint.tokens.text}',
              },
              text: {
                fontSize: 16,
                color: '${blueprint.tokens.text}',
              },
            });
        """.trimIndent()

        return AppForgeRendererOutput(
            entryPoint = "react-native/App.tsx",
            files = listOf(AppForgeGeneratedFile("react-native/App.tsx", app))
        )
    }
}

internal object AppForgeAgentWebRenderer {
    fun render(blueprint: AppForgeAgentBlueprint): AppForgeRendererOutput {
        val profile =
            AppForgeAgentPromptProductClassifier.classify(
                blueprint
            )

        return if (
            profile.kind ==
                AppForgeAgentProductKind.GAME
        ) {
            renderGame(
                blueprint = blueprint,
                mode =
                    profile.gameMode
                        ?: AppForgeAgentGameMode.ARCADE
            )
        } else {
            renderApplication(
                blueprint
            )
        }
    }

    private fun renderApplication(
        blueprint: AppForgeAgentBlueprint
    ): AppForgeRendererOutput {
        val screensJson = blueprint.screens.joinToString(",\n") { screen ->
            val components = screen.components.joinToString(",") { "\"${jsString(it)}\"" }
            val actions = screen.actions.joinToString(",") { action ->
                val target = action.targetRoute?.let { "\"${jsString(it)}\"" } ?: "null"
                "{id:\"${jsString(action.id)}\",label:\"${jsString(action.label)}\",target:$target}"
            }
            """  "${jsString(screen.route)}": { title: "${jsString(screen.title)}", purpose: "${jsString(screen.purpose)}", components: [$components], actions: [$actions] }"""
        }

        val html = """
            <!doctype html>
            <html lang="tr">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width,initial-scale=1" />
                <title>${htmlEscape(blueprint.appName)}</title>
                <link rel="stylesheet" href="styles.css" />
              </head>
              <body>
                <main id="app" aria-live="polite"></main>
                <script type="module" src="app.js"></script>
              </body>
            </html>
        """.trimIndent()

        val js = """
            const screens = {
$screensJson
            };

            let route = "${jsString(blueprint.startRoute)}";
            const root = document.querySelector('#app');

            function render() {
              const screen = screens[route] ?? screens["${jsString(blueprint.startRoute)}"];
              root.replaceChildren();

              const title = document.createElement('h1');
              title.textContent = screen.title;
              root.append(title);

              const purpose = document.createElement('p');
              purpose.textContent = screen.purpose;
              root.append(purpose);

              for (const component of screen.components) {
                const item = document.createElement('p');
                item.textContent = '• ' + component;
                root.append(item);
              }

              for (const action of screen.actions) {
                const button = document.createElement('button');
                button.type = 'button';
                button.textContent = action.label;
                button.addEventListener('click', () => {
                  if (action.target && screens[action.target]) {
                    route = action.target;
                    render();
                  }
                });
                root.append(button);
              }
            }

            render();
        """.trimIndent()

        val css = """
            :root {
              font-family: system-ui, sans-serif;
              color: ${blueprint.tokens.text};
              background: ${blueprint.tokens.background};
            }

            * { box-sizing: border-box; }

            body {
              margin: 0;
              min-height: 100vh;
              background: ${blueprint.tokens.background};
            }

            #app {
              max-width: 720px;
              margin: 0 auto;
              padding: ${blueprint.tokens.spacingUnitDp * 2}px;
              display: grid;
              gap: ${blueprint.tokens.spacingUnitDp}px;
            }

            button {
              border: 0;
              border-radius: ${blueprint.tokens.cornerRadiusDp}px;
              padding: ${blueprint.tokens.spacingUnitDp}px ${blueprint.tokens.spacingUnitDp * 2}px;
              background: ${blueprint.tokens.primary};
              color: ${contrastText(blueprint.tokens.primary)};
              cursor: pointer;
            }
        """.trimIndent()

        return AppForgeRendererOutput(
            entryPoint = "web/index.html",
            files = listOf(
                AppForgeGeneratedFile("web/index.html", html),
                AppForgeGeneratedFile("web/app.js", js),
                AppForgeGeneratedFile("web/styles.css", css)
            )
        )
    }

    private fun renderGame(
        blueprint: AppForgeAgentBlueprint,
        mode: AppForgeAgentGameMode
    ): AppForgeRendererOutput {
        val purpose =
            blueprint.screens
                .firstOrNull()
                ?.purpose
                ?.takeIf { it.isNotBlank() }
                ?: blueprint.prompt

        val modeValue =
            when (mode) {
                AppForgeAgentGameMode.RACING -> "racing"
                AppForgeAgentGameMode.ARCADE -> "arcade"
            }

        val html = """
            <!doctype html>
            <html lang="tr">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no" />
                <meta name="theme-color" content="${blueprint.tokens.background}" />
                <title>${htmlEscape(blueprint.appName)}</title>
                <link rel="stylesheet" href="styles.css" />
              </head>
              <body>
                <main class="shell">
                  <section class="hero">
                    <div>
                      <p class="eyebrow">AppForge AI • Yerel oyun</p>
                      <h1>${htmlEscape(blueprint.appName)}</h1>
                      <p>${htmlEscape(purpose)}</p>
                    </div>
                    <button id="restart" type="button">Yeniden başlat</button>
                  </section>

                  <section class="hud" aria-live="polite">
                    <span>Skor <strong id="score">0</strong></span>
                    <span>En iyi <strong id="best">0</strong></span>
                    <span id="status">Hazır</span>
                  </section>

                  <div class="game-wrap">
                    <canvas id="game" width="900" height="540" aria-label="Oyun alanı"></canvas>
                  </div>

                  <section class="controls">
                    <button id="left" type="button" aria-label="Sola git">◀</button>
                    <p id="hint">Dokun veya klavyeyi kullan</p>
                    <button id="right" type="button" aria-label="Sağa git">▶</button>
                  </section>
                </main>
                <script type="module" src="app.js"></script>
              </body>
            </html>
        """.trimIndent()

        val js = """
            const mode = "$modeValue";
            const canvas = document.querySelector('#game');
            const ctx = canvas.getContext('2d');
            const scoreNode = document.querySelector('#score');
            const bestNode = document.querySelector('#best');
            const statusNode = document.querySelector('#status');
            const restartButton = document.querySelector('#restart');
            const leftButton = document.querySelector('#left');
            const rightButton = document.querySelector('#right');
            const hintNode = document.querySelector('#hint');

            const storageKey = 'appforge-game-best-' + mode;
            let best = Number(localStorage.getItem(storageKey) || 0);
            let running = true;
            let score = 0;
            let last = performance.now();
            let elapsed = 0;
            let player = { x: 0.5, y: 0.84, w: 0.09, h: 0.10 };
            let target = { x: 0.5, y: 0.35, r: 0.055 };
            let obstacles = [];
            let spawnClock = 0;

            bestNode.textContent = String(best);
            hintNode.textContent = mode === 'racing'
              ? '◀ ▶ veya A / D • engellerden kaç'
              : 'Hedefe dokun • 45 saniyede en yüksek skoru yap';

            function clamp(value, min, max) {
              return Math.max(min, Math.min(max, value));
            }

            function reset() {
              running = true;
              score = 0;
              elapsed = 0;
              spawnClock = 0;
              obstacles = [];
              player.x = 0.5;
              moveTarget();
              scoreNode.textContent = '0';
              statusNode.textContent = 'Oynanıyor';
              last = performance.now();
            }

            function finish(reason) {
              if (!running) return;
              running = false;
              statusNode.textContent = reason;
              if (score > best) {
                best = score;
                localStorage.setItem(storageKey, String(best));
                bestNode.textContent = String(best);
              }
            }

            function addScore(amount) {
              score += amount;
              scoreNode.textContent = String(score);
            }

            function moveTarget() {
              target.x = 0.12 + Math.random() * 0.76;
              target.y = 0.14 + Math.random() * 0.64;
            }

            function movePlayer(direction) {
              if (!running) return;
              player.x = clamp(player.x + direction * 0.09, 0.08, 0.92);
            }

            function pointerToCanvas(event) {
              const rect = canvas.getBoundingClientRect();
              return {
                x: (event.clientX - rect.left) / rect.width,
                y: (event.clientY - rect.top) / rect.height
              };
            }

            function handlePointer(event) {
              if (!running) return;
              const point = pointerToCanvas(event);

              if (mode === 'racing') {
                movePlayer(point.x < 0.5 ? -1 : 1);
                return;
              }

              const dx = point.x - target.x;
              const dy = point.y - target.y;
              if (Math.hypot(dx, dy) <= target.r * 1.5) {
                addScore(10);
                moveTarget();
              }
            }

            function rectHit(a, b) {
              return Math.abs(a.x - b.x) < (a.w + b.w) / 2 &&
                Math.abs(a.y - b.y) < (a.h + b.h) / 2;
            }

            function updateRacing(dt) {
              spawnClock += dt;
              if (spawnClock > Math.max(0.32, 0.85 - elapsed * 0.006)) {
                spawnClock = 0;
                const lanes = [0.2, 0.4, 0.6, 0.8];
                obstacles.push({
                  x: lanes[Math.floor(Math.random() * lanes.length)],
                  y: -0.08,
                  w: 0.085,
                  h: 0.11,
                  speed: 0.32 + Math.min(0.42, elapsed * 0.004)
                });
              }

              for (const obstacle of obstacles) {
                obstacle.y += obstacle.speed * dt;
                if (rectHit(player, obstacle)) {
                  finish('Çarpışma • yeniden dene');
                  return;
                }
              }

              const passed = obstacles.filter(item => item.y > 1.12).length;
              if (passed > 0) addScore(passed * 5);
              obstacles = obstacles.filter(item => item.y <= 1.12);
            }

            function updateArcade() {
              if (elapsed >= 45) {
                finish('Süre bitti');
              }
            }

            function drawBackground() {
              ctx.fillStyle = '${blueprint.tokens.background}';
              ctx.fillRect(0, 0, canvas.width, canvas.height);

              if (mode === 'racing') {
                ctx.fillStyle = '${blueprint.tokens.surface}';
                ctx.fillRect(canvas.width * 0.12, 0, canvas.width * 0.76, canvas.height);
                ctx.strokeStyle = '${blueprint.tokens.secondary}';
                ctx.lineWidth = 5;
                ctx.setLineDash([24, 24]);
                for (const lane of [0.31, 0.5, 0.69]) {
                  ctx.beginPath();
                  ctx.moveTo(canvas.width * lane, 0);
                  ctx.lineTo(canvas.width * lane, canvas.height);
                  ctx.stroke();
                }
                ctx.setLineDash([]);
              }
            }

            function drawRacing() {
              const px = player.x * canvas.width;
              const py = player.y * canvas.height;
              const pw = player.w * canvas.width;
              const ph = player.h * canvas.height;

              ctx.fillStyle = '${blueprint.tokens.primary}';
              ctx.fillRect(px - pw / 2, py - ph / 2, pw, ph);
              ctx.fillStyle = '${contrastText(blueprint.tokens.primary)}';
              ctx.font = '700 24px system-ui';
              ctx.textAlign = 'center';
              ctx.fillText('AI', px, py + 8);

              ctx.fillStyle = '${blueprint.tokens.secondary}';
              for (const obstacle of obstacles) {
                const ox = obstacle.x * canvas.width;
                const oy = obstacle.y * canvas.height;
                const ow = obstacle.w * canvas.width;
                const oh = obstacle.h * canvas.height;
                ctx.fillRect(ox - ow / 2, oy - oh / 2, ow, oh);
              }
            }

            function drawArcade() {
              const x = target.x * canvas.width;
              const y = target.y * canvas.height;
              const radius = target.r * Math.min(canvas.width, canvas.height);

              ctx.beginPath();
              ctx.arc(x, y, radius, 0, Math.PI * 2);
              ctx.fillStyle = '${blueprint.tokens.primary}';
              ctx.fill();

              ctx.beginPath();
              ctx.arc(x, y, radius * 0.45, 0, Math.PI * 2);
              ctx.fillStyle = '${contrastText(blueprint.tokens.primary)}';
              ctx.fill();
            }

            function frame(now) {
              const dt = Math.min(0.05, Math.max(0, (now - last) / 1000));
              last = now;

              if (running) {
                elapsed += dt;
                if (mode === 'racing') {
                  updateRacing(dt);
                  if (running) addScore(Math.floor(dt * 10));
                } else {
                  updateArcade();
                }
              }

              drawBackground();
              if (mode === 'racing') drawRacing();
              else drawArcade();

              requestAnimationFrame(frame);
            }

            canvas.addEventListener('pointerdown', handlePointer);
            leftButton.addEventListener('pointerdown', () => movePlayer(-1));
            rightButton.addEventListener('pointerdown', () => movePlayer(1));
            restartButton.addEventListener('click', reset);

            window.addEventListener('keydown', event => {
              if (event.key === 'ArrowLeft' || event.key.toLowerCase() === 'a') {
                event.preventDefault();
                movePlayer(-1);
              }
              if (event.key === 'ArrowRight' || event.key.toLowerCase() === 'd') {
                event.preventDefault();
                movePlayer(1);
              }
              if (event.key === 'Enter' && !running) reset();
            });

            reset();
            requestAnimationFrame(frame);
        """.trimIndent()

        val css = """
            :root {
              font-family: Inter, ui-sans-serif, system-ui, sans-serif;
              color: ${blueprint.tokens.text};
              background: ${blueprint.tokens.background};
              -webkit-tap-highlight-color: transparent;
            }

            * { box-sizing: border-box; }

            body {
              margin: 0;
              min-height: 100vh;
              overscroll-behavior: none;
              background: ${blueprint.tokens.background};
            }

            button { font: inherit; touch-action: manipulation; }

            .shell {
              width: min(100%, 980px);
              margin: 0 auto;
              padding: 16px;
              display: grid;
              gap: 12px;
            }

            .hero {
              display: flex;
              justify-content: space-between;
              align-items: center;
              gap: 16px;
            }

            h1 { margin: 2px 0 6px; }
            p { margin: 0; }

            .eyebrow {
              color: ${blueprint.tokens.secondary};
              font-weight: 700;
              letter-spacing: .04em;
              text-transform: uppercase;
              font-size: 12px;
            }

            .hud,
            .controls {
              display: flex;
              align-items: center;
              justify-content: space-between;
              gap: 10px;
              padding: 12px;
              border-radius: ${blueprint.tokens.cornerRadiusDp}px;
              background: ${blueprint.tokens.surface};
            }

            .game-wrap {
              overflow: hidden;
              border-radius: ${blueprint.tokens.cornerRadiusDp}px;
              border: 1px solid ${blueprint.tokens.secondary};
              background: ${blueprint.tokens.surface};
            }

            canvas {
              display: block;
              width: 100%;
              aspect-ratio: 5 / 3;
              touch-action: none;
            }

            button {
              border: 0;
              border-radius: ${blueprint.tokens.cornerRadiusDp}px;
              padding: 12px 18px;
              min-height: 48px;
              background: ${blueprint.tokens.primary};
              color: ${contrastText(blueprint.tokens.primary)};
              font-weight: 800;
              cursor: pointer;
            }

            .controls button {
              min-width: 86px;
              font-size: 24px;
            }

            #hint {
              text-align: center;
              color: ${blueprint.tokens.text};
              opacity: .82;
            }

            @media (max-width: 640px) {
              .shell { padding: 10px; }
              .hero { align-items: flex-start; }
              .hero p:not(.eyebrow) { font-size: 13px; }
              .hud { font-size: 13px; }
              .controls button { min-width: 72px; }
            }
        """.trimIndent()

        return AppForgeRendererOutput(
            entryPoint = "web/index.html",
            files = listOf(
                AppForgeGeneratedFile("web/index.html", html),
                AppForgeGeneratedFile("web/app.js", js),
                AppForgeGeneratedFile("web/styles.css", css)
            )
        )
    }
}

private fun safeIdentifier(raw: String): String {
    val cleaned = raw
        .trim()
        .map { ch ->
            if (ch.code < 128 && ch.isLetterOrDigit()) ch else '_'
        }
        .joinToString("")
        .trim('_')
        .ifBlank { "app" }
    return if (cleaned.first().isDigit()) "app_$cleaned" else cleaned
}

private fun pascalIdentifier(raw: String): String {
    val parts = raw.split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
    val value = parts.joinToString("") { part ->
        part.lowercase().replaceFirstChar { it.uppercase() }
    }.ifBlank { "Screen" }
    return if (value.first().isDigit()) "Screen$value" else value
}

private fun kotlinString(raw: String): String = raw
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("$", "\\$")
    .replace("\n", "\\n")
    .replace("\r", "")

private fun dartString(raw: String): String = raw
    .replace("\\", "\\\\")
    .replace("'", "\\'")
    .replace("$", "\\$")
    .replace("\n", "\\n")
    .replace("\r", "")

private fun jsString(raw: String): String = raw
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "")

private fun jsxText(raw: String): String = raw
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("{", "&#123;")
    .replace("}", "&#125;")

private fun jsxAttribute(raw: String): String = jsxText(raw)
    .replace("\"", "&quot;")

private fun xmlEscape(raw: String): String = raw
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun htmlEscape(raw: String): String = xmlEscape(raw)

private fun androidColor(raw: String): String {
    val hex = raw.removePrefix("#").uppercase()
    val argb = if (hex.length == 8) {
        hex.takeLast(2) + hex.take(6)
    } else {
        "FF$hex"
    }
    return "0x${argb}L"
}

private fun flutterColor(raw: String): String {
    val hex = raw.removePrefix("#").uppercase()
    val argb = if (hex.length == 8) {
        hex.takeLast(2) + hex.take(6)
    } else {
        "FF$hex"
    }
    return "0x$argb"
}

private fun contrastText(raw: String): String {
    val hex = raw.removePrefix("#").take(6)
    val r = hex.substring(0, 2).toInt(16)
    val g = hex.substring(2, 4).toInt(16)
    val b = hex.substring(4, 6).toInt(16)
    val luminance = (299 * r + 587 * g + 114 * b) / 1000
    return if (luminance >= 150) "#000000" else "#FFFFFF"
}
