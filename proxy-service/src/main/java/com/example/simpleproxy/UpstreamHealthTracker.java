package com.example.simpleproxy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which backend instances failed recently, so the proxy
 * stops sending requests to a dead instance for a short cooldown.
 */
@Component
public class UpstreamHealthTracker {

    private final ConcurrentHashMap<String, Long> downUntil = new ConcurrentHashMap<>();
    private final long cooldownMs;

    public UpstreamHealthTracker(@Value("${proxy.health.cooldown-ms:10000}") long cooldownMs) {
        this.cooldownMs = cooldownMs;
    }

    public void markFailed(String url) {
        downUntil.put(url, System.currentTimeMillis() + cooldownMs);
    }

    public void markHealthy(String url) {
        downUntil.remove(url);
    }

    public boolean isAvailable(String url) {
        Long until = downUntil.get(url);
        return until == null || System.currentTimeMillis() >= until;
    }
}