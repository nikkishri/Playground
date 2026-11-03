package playground.des;

/**
 * Public entry point for the actor layer. Wraps a {@link SimulationEngine} and turns
 * message sends into delivery events. Each send produces an {@link Envelope} (an
 * {@link Event}) with exactly one recipient; executing the envelope invokes that
 * actor's {@link Actor#receive} with a fresh {@link DeliveryContext}.
 */
public class ActorSystem {

    private final SimulationEngine engine = new SimulationEngine();

    /**
     * @return the current simulation tick
     */
    public long now() {
        return engine.now();
    }

    /**
     * Injects a message from outside the actor system (no sender), delivered after
     * {@code delay} ticks.
     *
     * @param to      the recipient actor
     * @param message the immutable message
     * @param delay   non-negative ticks from now until delivery
     */
    public void tell(Actor to, Message message, long delay) {
        schedule(null, to, message, delay);
    }

    /**
     * Runs the simulation until the event queue is empty.
     */
    public void run() {
        engine.run();
    }

    /**
     * Runs the simulation until the queue is empty or the next event would fire past
     * {@code horizon} (inclusive).
     *
     * @param horizon the last tick (inclusive) at which events may fire
     */
    public void runUntil(long horizon) {
        engine.runUntil(horizon);
    }

    private void schedule(Actor from, Actor to, Message message, long delay) {
        if (to == null) {
            throw new IllegalArgumentException("recipient must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        engine.scheduleIn(delay, new Envelope(from, to, message));
    }

    /** A delivery event: one message, one recipient, one optional sender. */
    private final class Envelope extends Event {
        private final Actor from;
        private final Actor to;
        private final Message message;

        Envelope(Actor from, Actor to, Message message) {
            this.from = from;
            this.to = to;
            this.message = message;
        }

        @Override
        protected void execute(SimulationEngine engine) {
            to.receive(message, new DeliveryContext(this));
        }
    }

    /** Capabilities valid for the single delivery of one {@link Envelope}. */
    private final class DeliveryContext implements ActorContext {
        private final Envelope envelope;

        DeliveryContext(Envelope envelope) {
            this.envelope = envelope;
        }

        @Override
        public long now() {
            return engine.now();
        }

        @Override
        public Actor self() {
            return envelope.to;
        }

        @Override
        public Actor sender() {
            return envelope.from;
        }

        @Override
        public void send(Actor to, Message message) {
            send(to, message, 0L);
        }

        @Override
        public void send(Actor to, Message message, long delay) {
            schedule(envelope.to, to, message, delay);
        }

        @Override
        public void reply(Message message) {
            if (envelope.from == null) {
                throw new IllegalStateException("cannot reply: message has no sender");
            }
            schedule(envelope.to, envelope.from, message, 0L);
        }

        @Override
        public void stop() {
            engine.stop();
        }
    }
}
