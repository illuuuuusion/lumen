package net.illunium.lumen.core;

import java.time.Duration;
import java.time.Instant;

/** Bot health: when it started and whether the gateway connection is up. */
public final class HealthState {

    private final Instant startedAt = Instant.now();
    private volatile boolean ready;

    public void markReady(boolean value) {
        this.ready = value;
    }

    public boolean ready() {
        return ready;
    }

    public Duration uptime() {
        return Duration.between(startedAt, Instant.now());
    }
}
