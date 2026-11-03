package playground.des;

/**
 * An abstract discrete-event. The engine assigns each event a {@link #time} and a
 * monotonically increasing {@link #sequence} number when it is scheduled; subclasses
 * must not set these fields themselves.
 *
 * <p>Events are ordered by {@code time -> priority -> sequence} so that simultaneous
 * events execute deterministically (lower priority value first, then FIFO by insertion).
 */
public abstract class Event {

    /** Simulation tick at which this event fires. Set by the engine on scheduling. */
    long time;

    /** Insertion order used as the final, deterministic tie-breaker. Set by the engine. */
    long sequence;

    /** True once the engine has scheduled this event, guarding against double-scheduling. */
    boolean scheduled;

    /**
     * Tie-break priority for events at the same {@link #time}. Lower values fire first.
     * Defaults to {@code 0}; override for higher- or lower-priority events.
     *
     * @return the priority of this event
     */
    public int priority() {
        return 0;
    }

    /**
     * The tick at which this event is scheduled to fire.
     *
     * @return the event time
     */
    public final long time() {
        return time;
    }

    /**
     * Executes this event's effect. Called by the engine when the event is dequeued,
     * at which point the engine clock already reads {@link #time()}.
     *
     * @param engine the engine running this event, used to schedule follow-up events
     */
    protected abstract void execute(SimulationEngine engine);
}
