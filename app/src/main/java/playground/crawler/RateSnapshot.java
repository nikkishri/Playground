package playground.crawler;

import java.util.Map;

/** Immutable per-domain rate config, swapped atomically on refresh. */
public final class RateSnapshot {

    private final Map<String, Double> ratesPerSec;

    RateSnapshot(Map<String, Double> ratesPerSec) {
        this.ratesPerSec = Map.copyOf(ratesPerSec); // immutable defensive copy
    }

    /** @return permits/sec for the domain, or null if the domain is unconfigured. */
    Double rateFor(String domain) {
        return ratesPerSec.get(domain);
    }
}
