package playground.des;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * A single-threaded discrete-event scheduler with a monotonic integer clock.
 *
 * <p>Events are held in a {@link PriorityQueue} ordered by
 * {@code time -> priority -> insertion sequence}. The run loop repeatedly dequeues the
 * earliest event, advances the clock to that event's time, and executes it. The clock
 * never moves backwards and is only ever set to a dequeued event's time.
 */
public class SimulationEngine {

    private static final Comparator<Event> ORDER =
            Comparator.comparingLong((Event e) -> e.time)
                    .thenComparingInt(Event::priority)
                    .thenComparingLong(e -> e.sequence);

    private final PriorityQueue<Event> queue = new PriorityQueue<>(ORDER);

    private long now = 0L;
    private long nextSequence = 0L;
    private boolean stopped = false;

    /**
     * The current simulation time. Advances only as events are executed.
     *
     * @return the current tick
     */
    public long now() {
        return now;
    }

    /**
     * Schedules an event to fire {@code delay} ticks from {@link #now()}.
     *
     * @param delay non-negative number of ticks from now
     * @param event the event to schedule
     * @throws IllegalArgumentException if {@code delay} is negative
     * @throws IllegalStateException    if the event has already been scheduled
     */
    public void scheduleIn(long delay, Event event) {
        if (delay < 0) {
            throw new IllegalArgumentException("delay must be non-negative: " + delay);
        }
        scheduleAt(now + delay, event);
    }

    /**
     * Schedules an event to fire at absolute tick {@code time}.
     *
     * @param time  the tick at which to fire; must not be in the past
     * @param event the event to schedule
     * @throws IllegalArgumentException if {@code time < now()}
     * @throws IllegalStateException    if the event has already been scheduled
     */
    public void scheduleAt(long time, Event event) {
        if (time < now) {
            throw new IllegalArgumentException(
                    "cannot schedule in the past: time=" + time + " now=" + now);
        }
        if (event.scheduled) {
            throw new IllegalStateException("event already scheduled");
        }
        event.time = time;
        event.sequence = nextSequence++;
        event.scheduled = true;
        queue.add(event);
    }

    /**
     * Runs until the event queue is empty or {@link #stop()} is called.
     */
    public void run() {
        runUntil(Long.MAX_VALUE);
    }

    /**
     * Runs until the queue is empty, {@link #stop()} is called, or the next event's
     * time would exceed {@code horizon}. Events scheduled exactly at {@code horizon}
     * are executed (inclusive horizon); the clock is left at {@code horizon} if it was
     * reached, even when no event fires there.
     *
     * @param horizon the last tick (inclusive) at which events may fire
     */
    public void runUntil(long horizon) {
        stopped = false;
        while (!stopped) {
            Event next = queue.peek();
            if (next == null || next.time > horizon) {
                break;
            }
            queue.poll();
            now = next.time;
            next.execute(this);
        }
    }

    /**
     * Requests the run loop to stop after the current event completes.
     */
    public void stop() {
        stopped = true;
    }
}
