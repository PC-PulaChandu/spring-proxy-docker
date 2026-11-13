package com.example.simpleproxy;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class UpstreamHealthTrackerTest {

    private static final String URL = "http://backend:8080";

    @Test
    void unknownUrlIsAvailable() {
        UpstreamHealthTracker tracker = new UpstreamHealthTracker(60_000);

        assertThat(tracker.isAvailable(URL)).isTrue();
    }

    @Test
    void failedUrlIsUnavailableDuringCooldown() {
        UpstreamHealthTracker tracker = new UpstreamHealthTracker(60_000);

        tracker.markFailed(URL);

        assertThat(tracker.isAvailable(URL)).isFalse();
    }

    @Test
    void markHealthyClearsTheFailure() {
        UpstreamHealthTracker tracker = new UpstreamHealthTracker(60_000);
        tracker.markFailed(URL);

        tracker.markHealthy(URL);

        assertThat(tracker.isAvailable(URL)).isTrue();
    }

    @Test
    void urlBecomesAvailableAgainAfterTheCooldown() {
        UpstreamHealthTracker tracker = new UpstreamHealthTracker(100);
        tracker.markFailed(URL);

        await().atMost(Duration.ofSeconds(2)).until(() -> tracker.isAvailable(URL));
    }
}