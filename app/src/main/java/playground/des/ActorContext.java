package playground.des;

/**
 * Per-delivery capabilities handed to an {@link Actor} when it receives a message.
 *
 * <p>A context is valid only for the duration of a single {@link Actor#receive} call.
 * It exposes the clock, the receiving actor ({@link #self()}), the message's
 * {@link #sender()} (may be {@code null} for externally injected messages), and the
 * means to send further messages, reply to the sender, or stop the simulation.
 */
public interface ActorContext {

    /**
     * @return the current simulation tick
     */
    long now();

    /**
     * @return the actor currently receiving the message
     */
    Actor self();

    /**
     * @return the actor that sent the message, or {@code null} if injected externally
     */
    Actor sender();

    /**
     * Sends a message to be delivered to {@code to} at the current tick.
     *
     * @param to      the recipient actor
     * @param message the immutable message
     */
    void send(Actor to, Message message);

    /**
     * Sends a message to be delivered to {@code to} after {@code delay} ticks, modelling
     * a service or transit time.
     *
     * @param to      the recipient actor
     * @param message the immutable message
     * @param delay   non-negative ticks from now until delivery
     */
    void send(Actor to, Message message, long delay);

    /**
     * Replies to the sender of the current message, delivered at the current tick.
     *
     * @param message the immutable message
     * @throws IllegalStateException if there is no sender (externally injected message)
     */
    void reply(Message message);

    /**
     * Requests the simulation to stop after the current delivery completes.
     */
    void stop();
}
