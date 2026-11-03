package playground.crawler;

import java.util.Map;

/** Source of per-domain rates (permits/sec). Polled by the refresher; later the fleet seam. */
public interface RateProvider {
    Map<String, Double> currentRates();
}
