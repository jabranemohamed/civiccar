package tn.civiccare.shared;

import org.springframework.stereotype.Component;
import tn.civiccare.observability.Telemetry.ValidationException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limitation de débit en mémoire (fenêtre fixe) pour dépôt, contact et activation
 * d'abonnement. Suffisant pour un déploiement mono-instance ; documenté comme limite.
 */
@Component
public class RateLimiter {

    private record Window(Instant start, AtomicInteger count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Lève une ValidationException si la limite est dépassée. */
    public void check(String key, int maxPerWindow, long windowSeconds) {
        Instant now = clock.instant();
        Window window = windows.compute(key, (k, w) -> {
            if (w == null || now.isAfter(w.start().plusSeconds(windowSeconds))) {
                return new Window(now, new AtomicInteger());
            }
            return w;
        });
        if (window.count().incrementAndGet() > maxPerWindow) {
            throw new ValidationException("rate.limited");
        }
        // Nettoyage opportuniste des fenêtres expirées
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now.isAfter(e.getValue().start().plusSeconds(windowSeconds * 2)));
        }
    }
}
