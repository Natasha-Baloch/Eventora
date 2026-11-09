package com.eventora.service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Locks password sign-in for an email for 10 minutes after 5 failures (brute-force protection). */
@Component
public class LoginThrottle {
    private record State(int fails, Instant lockedUntil) {}
    private final ConcurrentHashMap<String, State> map = new ConcurrentHashMap<>();

    public boolean isLocked(String email) {
        State s = map.get(email);
        return s != null && s.lockedUntil() != null && s.lockedUntil().isAfter(Instant.now());
    }
    public void fail(String email) {
        map.merge(email, new State(1, null), (o, n) -> o.fails() + 1 >= 5
            ? new State(0, Instant.now().plusSeconds(600)) : new State(o.fails() + 1, null));
    }
    public void reset(String email) { map.remove(email); }
}
