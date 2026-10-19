package com.appforge.runtime;

import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordedEvent;
import java.util.HashMap;
import java.util.Map;
import java.time.Instant;

/** Observe SDK-manager socket reads; never intercept TLS or manufacture an HTTP total. */
public final class NetworkByteAgent {
    private static final AtomicLong bytes = new AtomicLong();
    private static final long started = System.nanoTime();
    private static final String transfer = "sdk-jvm-" + UUID.randomUUID();
    private static Path output;
    private static volatile boolean closed;
    private static synchronized void emit(boolean finished, boolean available) {
        if (closed && !finished) return;
        try {
            long elapsed = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            String label = available ? "SDK Manager: gozlenen sifreli soket baytlari (payload toplami bilinmiyor)" : "SDK Manager: JVM ag olcumu kullanilamiyor; toplam boyut bilinmiyor";
            String body = "{\"schema\":1,\"transfer\":\"" + transfer + "\",\"component\":\"" + label + "\",\"byteMetric\":\"" + (available ? "wire-bytes" : "unavailable") + "\",\"bytes\":" + bytes.get() + ",\"total\":null,\"elapsedMs\":" + elapsed + ",\"finished\":" + finished + "}";
            Path channels = Paths.get(output.toString() + ".events");
            Files.createDirectories(channels);
            Path channel = channels.resolve(transfer + ".json");
            Path channelPart = channels.resolve(transfer + ".json.part");
            Files.write(channelPart, body.getBytes(StandardCharsets.UTF_8));
            Files.move(channelPart, channel, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Path temporary = Paths.get(output.toString() + ".jvm-" + transfer);
            Files.write(temporary, body.getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) { /* Telemetry cannot change SDK resolution or certificate validation. */ }
    }
    public static void premain(String options, Instrumentation unused) {
        String path = System.getenv("APPFORGE_DOWNLOAD_TELEMETRY");
        if (path == null || path.isEmpty()) return;
        output = Paths.get(path);
        try {
            if (!FlightRecorder.isAvailable()) { emit(true, false); return; }
            Recording recording = new Recording();
            recording.setMaxSize(4L * 1024 * 1024);
            recording.setMaxAge(Duration.ofSeconds(10));
            recording.enable("jdk.SocketRead").withThreshold(Duration.ZERO).withoutStackTrace();
            recording.start();
            // RecordingStream owns a non-daemon thread in supported JDKs. A bounded recording
            // with a daemon sampler avoids keeping sdkmanager alive after main has returned.
            Map<String, Instant> seen = new HashMap<>();
            Runnable sample = () -> {
                synchronized (seen) {
                    Path dump = null;
                    try {
                        dump = Files.createTempFile(output.toAbsolutePath().getParent(), "sdk-read-", ".jfr");
                        recording.dump(dump);
                        Instant cutoff = Instant.now().minusSeconds(12);
                        seen.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
                        try (RecordingFile events = new RecordingFile(dump)) {
                            while (events.hasMoreEvents()) {
                                RecordedEvent event = events.readEvent();
                                if (!"jdk.SocketRead".equals(event.getEventType().getName())) continue;
                                long count = event.getLong("bytesRead");
                                if (count <= 0) continue;
                                String identity = event.getStartTime() + ":" + event.getDuration() + ":" +
                                        event.getThread().getJavaThreadId() + ":" + count;
                                if (seen.putIfAbsent(identity, event.getStartTime()) == null) bytes.addAndGet(count);
                            }
                        }
                    } catch (Throwable ignored) { /* Unsupported observation remains indeterminate. */ }
                    finally { if (dump != null) try { Files.deleteIfExists(dump); } catch (Throwable ignored) {} }
                }
            };
            ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "AppForgeSdkByteObserver"); thread.setDaemon(true); return thread;
            });
            timer.scheduleAtFixedRate(() -> { sample.run(); emit(false, true); }, 0, 1000, TimeUnit.MILLISECONDS);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                closed = true; timer.shutdownNow();
                sample.run();
                try { recording.close(); } catch (Throwable ignored) {}
                emit(true, true);
            }, "AppForgeSdkByteObserverClose"));
        } catch (Throwable unsupported) { emit(true, false); }
    }
}
