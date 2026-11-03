package playground.crawler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-domain token-bucket rate limiter, per-machine. Rates come from a {@link RateProvider}
 * via an immutable {@link RateSnapshot} held behind a volatile reference, so refresh never
 * contends with the acquire path.
 *
 * <p>Skeleton: {@link #tryAcquire} (the token-bucket algorithm) is not implemented yet.
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final RateProvider provider;
    private final double burstSeconds;
    private final LongSupplier nanoClock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile RateSnapshot snapshot;

    public TokenBucketRateLimiter(RateProvider provider, double burstSeconds, LongSupplier nanoClock) {
        this.provider = Objects.requireNonNull(provider, "provider");
        if (burstSeconds <= 0) {
            throw new IllegalArgumentException("burstSeconds must be > 0: " + burstSeconds);
        }
        this.burstSeconds = burstSeconds;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        RateSnapshot initial = load();
        this.snapshot = (initial != null) ? initial : new RateSnapshot(Map.of());
    }

    /** Pulls the latest rates and atomically swaps the snapshot; keeps last-good on failure. */
    public void refresh() {
        RateSnapshot next = load();
        if (next != null) {
            this.snapshot = next;
        }
    }

    private RateSnapshot load() {
        try {
            Map<String, Double> raw = provider.currentRates();
            Map<String, Double> clean = new HashMap<>();
            for (Map.Entry<String, Double> e : raw.entrySet()) {
                Double r = e.getValue();
                if (e.getKey() != null && r != null && r > 0 && !r.isNaN()) {
                    clean.put(e.getKey(), r);
                }
            }
            return new RateSnapshot(clean);
        } catch (RuntimeException ex) {
            return null; // caller keeps last-good
        }
    }

    @Override
    public boolean tryAcquire(String domain) {
        Double rate = snapshot.rateFor(domain);
        if (rate == null) {
            return false; // unconfigured domain: deny (§3 policy)
        }
        double capacity = rate * burstSeconds;
        Bucket bucket = buckets.computeIfAbsent(domain,
                d -> new Bucket(capacity, nanoClock.getAsLong()));
        return bucket.tryConsume(nanoClock.getAsLong(), rate, capacity);
    }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;

        Bucket(double tokens, long nowNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = nowNanos;
        }

        synchronized boolean tryConsume(long nowNanos, double ratePerSec, double capacity) {
            long elapsed = nowNanos - lastRefillNanos;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed / NANOS_PER_SECOND * ratePerSec);
                lastRefillNanos = nowNanos;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
