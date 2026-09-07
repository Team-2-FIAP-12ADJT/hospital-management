package com.fiap.hospital.gateway.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

@Component
public class ProxyBodyReader {

    private final long timeoutNanos;
    private final ScheduledThreadPoolExecutor deadlineWatchdog;

    private enum ReadOutcome {
        PENDENTE,
        PRAZO_VENCIDO,
        RESOLVIDO
    }

    public ProxyBodyReader(com.fiap.hospital.gateway.config.GatewayProperties properties) {
        this.timeoutNanos = properties.proxyReadTimeout().toNanos();
        deadlineWatchdog = new ScheduledThreadPoolExecutor(1, Thread.ofVirtual().factory());
        deadlineWatchdog.setRemoveOnCancelPolicy(true);
    }

    public byte[] read(InputStream body, long started) throws IOException {
        var outcome = new AtomicReference<>(ReadOutcome.PENDENTE);
        var remaining = timeoutNanos - (System.nanoTime() - started);
        var watchdog = deadlineWatchdog.schedule(() -> {
            if (outcome.compareAndSet(ReadOutcome.PENDENTE, ReadOutcome.PRAZO_VENCIDO)) {
                Thread.startVirtualThread(() -> closeQuietly(body));
            }
        }, Math.max(remaining, 0), TimeUnit.NANOSECONDS);

        try {
            var bytes = body.readAllBytes();
            if (!outcome.compareAndSet(ReadOutcome.PENDENTE, ReadOutcome.RESOLVIDO)) {
                throw new ProxyBodyReadTimeoutException(
                    new IOException("prazo vencido antes da conclusao da leitura")
                );
            }
            return bytes;
        } catch (IOException exception) {
            if (!outcome.compareAndSet(ReadOutcome.PENDENTE, ReadOutcome.RESOLVIDO)) {
                throw new ProxyBodyReadTimeoutException(exception);
            }
            if (System.nanoTime() - started >= timeoutNanos) {
                throw new ProxyBodyReadTimeoutException(exception);
            }
            throw exception;
        } finally {
            watchdog.cancel(false);
        }
    }

    @jakarta.annotation.PreDestroy
    void close() {
        deadlineWatchdog.shutdownNow();
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // O fechamento interrompe a leitura; sua falha nao muda o desfecho.
        }
    }

    static final class ProxyBodyReadTimeoutException extends IOException {

        private ProxyBodyReadTimeoutException(IOException cause) {
            super("Upstream body read timed out", cause);
        }
    }
}
