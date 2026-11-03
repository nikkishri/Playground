package playground.crawler;

public interface RateLimiter {
    boolean tryAcquire(String domain);
}
