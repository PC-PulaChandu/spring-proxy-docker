package com.example.simpleproxy;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class RequestLogService {

    private final MonitoringRecordRepository repository;
    private final ThreadPoolExecutor executor;

    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong saved = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public RequestLogService(MonitoringRecordRepository repository,
            @Value("${proxy.logging.core-pool-size:2}") int corePoolSize,
            @Value("${proxy.logging.max-pool-size:4}") int maxPoolSize,
            @Value("${proxy.logging.queue-capacity:1000}") int queueCapacity) {
        this.repository = repository;
        AtomicInteger threadNumber = new AtomicInteger(1);
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                runnable -> new Thread(runnable, "log-writer-" + threadNumber.getAndIncrement()),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Called on the request thread: only queues the work, never touches the
     * database.
     */
    public void log(MonitoringRecord record) {
        submitted.incrementAndGet();
        executor.execute(() -> {
            try {
                repository.save(record);
                saved.incrementAndGet();
                log.debug("Saved monitoring record for {}", record.getRequestPath());
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Could not save monitoring record for {}: {}",
                        record.getRequestPath(), e.getMessage());
            }
        });
    }

    public Map<String, Object> stats() {
        return Map.of(
                "submitted", submitted.get(),
                "saved", saved.get(),
                "failed", failed.get(),
                "queued", executor.getQueue().size(),
                "activeThreads", executor.getActiveCount(),
                "poolSize", executor.getPoolSize());
    }

    /** Runs when the application stops: finish the queued work before exiting. */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down log writer, {} records still queued", executor.getQueue().size());
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Forcing shutdown, dropping {} queued records",
                        executor.shutdownNow().size());
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}