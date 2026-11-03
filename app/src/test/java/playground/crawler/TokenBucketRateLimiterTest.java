package playground.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    private static final long SECOND = 1_000_000_000L;

    /** Mutable provider so tests can change rates and simulate failures. */
    private static final class FakeProvider implements RateProvider {
        volatile Map<String, Double> rates;
        volatile boolean fail;

        FakeProvider(Map<String, Double> rates) {
            this.rates = rates;
        }

        @Override
        public Map<String, Double> currentRates() {
            if (fail) {
                throw new RuntimeException("provider down");
            }
            return rates;
        }
    }

    private static TokenBucketRateLimiter limiter(Map<String, Double> rates, LongSupplier clock) {
        return new TokenBucketRateLimiter(new FakeProvider(rates), 1.0, clock);
    }

    // --- construction (fail-fast boundary) ---

    @Test
    void constructor_nullProvider_throwsNpe() {
        assertThrows(NullPointerException.class,
                () -> new TokenBucketRateLimiter(null, 1.0, () -> 0L));
    }

    @Test
    void constructor_nonPositiveBurst_throwsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(new FakeProvider(Map.of()), 0.0, () -> 0L));
    }

    // --- core behavior ---

    @Test
    void tryAcquire_configuredDomain_startsFull() {
        long[] clock = {0L};
        var limiter = limiter(Map.of("a.com", 5.0), () -> clock[0]);
        assertTrue(limiter.tryAcquire("a.com"));
    }

    @Test
    void tryAcquire_belowCapacity_grantsThenThrottles() {
        long[] clock = {0L};
        var limiter = limiter(Map.of("a.com", 3.0), () -> clock[0]);
        assertTrue(limiter.tryAcquire("a.com"));
        assertTrue(limiter.tryAcquire("a.com"));
        assertTrue(limiter.tryAcquire("a.com"));
        assertFalse(limiter.tryAcquire("a.com"));
    }

    @Test
    void tryAcquire_afterTimePasses_refills() {
        long[] clock = {0L};
        var limiter = limiter(Map.of("a.com", 1.0), () -> clock[0]);
        assertTrue(limiter.tryAcquire("a.com"));
        assertFalse(limiter.tryAcquire("a.com"));
        clock[0] += SECOND;
        assertTrue(limiter.tryAcquire("a.com"));
    }

    @Test
    void tryAcquire_longIdle_refillCappedAtCapacity() {
        long[] clock = {0L};
        var limiter = limiter(Map.of("a.com", 2.0), () -> clock[0]);
        clock[0] += 100 * SECOND; // would overflow if uncapped
        assertTrue(limiter.tryAcquire("a.com"));
        assertTrue(limiter.tryAcquire("a.com"));
        assertFalse(limiter.tryAcquire("a.com"));
    }

    @Test
    void tryAcquire_differentDomains_independentBuckets() {
        long[] clock = {0L};
        var limiter = limiter(Map.of("a.com", 1.0, "b.com", 1.0), () -> clock[0]);
        assertTrue(limiter.tryAcquire("a.com"));
        assertTrue(limiter.tryAcquire("b.com"));
        assertFalse(limiter.tryAcquire("a.com"));
    }

    // --- policy ---

    @Test
    void tryAcquire_unknownDomain_denied() {
        long[] clock = {0L};
        var limiter = limiter(Map.of("a.com", 1.0), () -> clock[0]);
        assertFalse(limiter.tryAcquire("unconfigured.com"));
    }

    // --- dynamic refresh ---

    @Test
    void refresh_rateIncreased_newRateApplied() {
        long[] clock = {0L};
        var provider = new FakeProvider(new HashMap<>(Map.of("a.com", 1.0)));
        var limiter = new TokenBucketRateLimiter(provider, 1.0, () -> clock[0]);

        assertTrue(limiter.tryAcquire("a.com"));
        assertFalse(limiter.tryAcquire("a.com")); // capacity 1 drained

        provider.rates = new HashMap<>(Map.of("a.com", 10.0));
        limiter.refresh();
        clock[0] += SECOND; // refill at the new rate

        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire("a.com"), "grant " + i);
        }
        assertFalse(limiter.tryAcquire("a.com"));
    }

    @Test
    void refresh_providerFails_keepsLastGood() {
        long[] clock = {0L};
        var provider = new FakeProvider(new HashMap<>(Map.of("a.com", 2.0)));
        var limiter = new TokenBucketRateLimiter(provider, 1.0, () -> clock[0]);

        assertTrue(limiter.tryAcquire("a.com"));
        assertTrue(limiter.tryAcquire("a.com"));
        assertFalse(limiter.tryAcquire("a.com"));

        provider.fail = true;
        limiter.refresh(); // should keep last-good (rate 2)

        clock[0] += SECOND;
        assertTrue(limiter.tryAcquire("a.com"));
        assertTrue(limiter.tryAcquire("a.com"));
        assertFalse(limiter.tryAcquire("a.com"));
    }

    // --- accuracy under concurrency (headline) ---

    @Test
    void tryAcquire_concurrentCallers_neverOverGrants() throws InterruptedException {
        long[] clock = {0L}; // frozen: no refill during the race
        int capacity = 100;
        int threads = 200;
        var limiter = limiter(Map.of("a.com", (double) capacity), () -> clock[0]);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger granted = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (limiter.tryAcquire("a.com")) {
                        granted.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();
        pool.shutdown();

        assertEquals(capacity, granted.get());
    }
}
