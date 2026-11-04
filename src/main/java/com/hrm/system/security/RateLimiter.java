package com.hrm.system.security;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window limiter. Counters reset on restart and are per-instance, which suits the
 * single Railway instance this app runs on. Move to a shared store if it is ever scaled out.
 */
@Component
public class RateLimiter {

    private static final String TOO_MANY = "Too many attempts. Please wait a few minutes and try again.";

    private record Window(long startedAt, long lengthMs, int count) {
        boolean expired(long now) {
            return now - startedAt >= lengthMs;
        }
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** Counts this call, then throws 429 once more than {@code limit} calls landed in the window. */
    public void consume(String key, int limit, Duration window) {
        record(key, window);
        requireUnder(key, limit + 1);
    }

    /** Counts an event, such as a failed login, without blocking it. */
    public void record(String key, Duration window) {
        long now = System.currentTimeMillis();
        windows.compute(key, (k, w) -> (w == null || w.expired(now))
                ? new Window(now, window.toMillis(), 1)
                : new Window(w.startedAt(), w.lengthMs(), w.count() + 1));
    }

    /** Throws 429 when the key has already reached the limit in its current window. */
    public void requireUnder(String key, int limit) {
        Window w = windows.get(key);
        if (w != null && !w.expired(System.currentTimeMillis()) && w.count() >= limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY);
        }
    }

    public void clear(String key) {
        windows.remove(key);
    }

    @Scheduled(fixedDelay = 600_000)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        windows.values().removeIf(w -> w.expired(now));
    }
}
