package playground.des;

/**
 * Base class for actors. An actor owns private state and interacts with the rest of the
 * system only by receiving immutable {@link Message}s. Because the simulation is
 * single-threaded and each delivery is one event handled by exactly one actor,
 * {@link #receive} is never re-entered concurrently and needs no locks.
 */
public abstract class Actor {

    private final String id;

    /**
     * @param id a human-readable identifier, unique within an {@link ActorSystem} by convention
     */
    protected Actor(String id) {
        this.id = id;
    }

    /**
     * @return this actor's identifier
     */
    public final String id() {
        return id;
    }

    /**
     * Handles one delivered message. This is the only path that touches this actor's
     * state. Use the supplied {@code context} to read the clock, discover the sender,
     * and send or reply with further messages.
     *
     * @param message the delivered message
     * @param context per-delivery capabilities for this receipt
     */
    protected abstract void receive(Message message, ActorContext context);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + id + ")";
    }
}
