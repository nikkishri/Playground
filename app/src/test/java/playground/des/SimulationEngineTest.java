package playground.des;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationEngineTest {

    /** Records the clock reading and a label each time it executes. */
    private static final class RecordingEvent extends Event {
        private final String label;
        private final int priority;
        private final List<String> log;
        private final List<Long> clockReadings;

        RecordingEvent(String label, List<String> log, List<Long> clockReadings) {
            this(label, 0, log, clockReadings);
        }

        RecordingEvent(String label, int priority, List<String> log, List<Long> clockReadings) {
            this.label = label;
            this.priority = priority;
            this.log = log;
            this.clockReadings = clockReadings;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        protected void execute(SimulationEngine engine) {
            log.add(label);
            clockReadings.add(engine.now());
        }
    }

    @Test
    void executesEventsInTimeOrder() {
        SimulationEngine engine = new SimulationEngine();
        List<String> log = new ArrayList<>();
        List<Long> clock = new ArrayList<>();

        engine.scheduleAt(30, new RecordingEvent("c", log, clock));
        engine.scheduleAt(10, new RecordingEvent("a", log, clock));
        engine.scheduleAt(20, new RecordingEvent("b", log, clock));

        engine.run();

        assertEquals(List.of("a", "b", "c"), log);
        assertEquals(List.of(10L, 20L, 30L), clock);
    }

    @Test
    void simultaneousEventsOrderByPriorityThenFifo() {
        SimulationEngine engine = new SimulationEngine();
        List<String> log = new ArrayList<>();
        List<Long> clock = new ArrayList<>();

        // All at t=5. Lower priority value fires first; equal priority keeps FIFO order.
        engine.scheduleAt(5, new RecordingEvent("p1-first", 1, log, clock));
        engine.scheduleAt(5, new RecordingEvent("p0-first", 0, log, clock));
        engine.scheduleAt(5, new RecordingEvent("p0-second", 0, log, clock));
        engine.scheduleAt(5, new RecordingEvent("p1-second", 1, log, clock));

        engine.run();

        assertEquals(List.of("p0-first", "p0-second", "p1-first", "p1-second"), log);
    }

    @Test
    void clockIsMonotonicAndSetFromEvents() {
        SimulationEngine engine = new SimulationEngine();
        List<String> log = new ArrayList<>();
        List<Long> clock = new ArrayList<>();

        assertEquals(0L, engine.now());
        engine.scheduleAt(100, new RecordingEvent("x", log, clock));
        engine.scheduleAt(50, new RecordingEvent("y", log, clock));

        engine.run();

        long previous = Long.MIN_VALUE;
        for (long reading : clock) {
            assertTrue(reading >= previous, "clock must not move backwards");
            previous = reading;
        }
        assertEquals(100L, engine.now());
    }

    @Test
    void rejectsSchedulingInThePast() {
        SimulationEngine engine = new SimulationEngine();
        List<String> log = new ArrayList<>();
        List<Long> clock = new ArrayList<>();

        engine.scheduleAt(10, new RecordingEvent("advance", log, clock));
        engine.run();
        assertEquals(10L, engine.now());

        assertThrows(IllegalArgumentException.class,
                () -> engine.scheduleAt(5, new RecordingEvent("past", log, clock)));
    }

    @Test
    void rejectsNegativeDelay() {
        SimulationEngine engine = new SimulationEngine();
        List<String> log = new ArrayList<>();
        List<Long> clock = new ArrayList<>();

        assertThrows(IllegalArgumentException.class,
                () -> engine.scheduleIn(-1, new RecordingEvent("neg", log, clock)));
    }

    @Test
    void rejectsDoubleScheduling() {
        SimulationEngine engine = new SimulationEngine();
        List<String> log = new ArrayList<>();
        List<Long> clock = new ArrayList<>();

        Event once = new RecordingEvent("once", log, clock);
        engine.scheduleAt(5, once);

        assertThrows(IllegalStateException.class, () -> engine.scheduleAt(6, once));
    }

    @Test
    void runUntilIsInclusiveOfHorizon() {
        SimulationEngine engine = new SimulationEngine();
        List<String> log = new ArrayList<>();
        List<Long> clock = new ArrayList<>();

        engine.scheduleAt(5, new RecordingEvent("at-5", log, clock));
        engine.scheduleAt(10, new RecordingEvent("at-10", log, clock));
        engine.scheduleAt(15, new RecordingEvent("at-15", log, clock));

        engine.runUntil(10);

        // Events at and before the horizon fire; the later one does not.
        assertEquals(List.of("at-5", "at-10"), log);
        assertEquals(10L, engine.now());

        // Remaining event still fires on a subsequent unbounded run.
        engine.run();
        assertEquals(List.of("at-5", "at-10", "at-15"), log);
        assertEquals(15L, engine.now());
    }

    @Test
    void scheduleInIsRelativeToCurrentTime() {
        SimulationEngine engine = new SimulationEngine();
        List<String> log = new ArrayList<>();
        List<Long> clock = new ArrayList<>();

        // Chained relative scheduling: first fires at 5, then schedules +7 -> 12.
        engine.scheduleIn(5, new Event() {
            @Override
            protected void execute(SimulationEngine e) {
                log.add("first");
                clock.add(e.now());
                e.scheduleIn(7, new RecordingEvent("second", log, clock));
            }
        });

        engine.run();

        assertEquals(List.of("first", "second"), log);
        assertEquals(List.of(5L, 12L), clock);
    }

    @Test
    void stopHaltsTheRunLoop() {
        SimulationEngine engine = new SimulationEngine();
        List<String> log = new ArrayList<>();
        List<Long> clock = new ArrayList<>();

        engine.scheduleAt(1, new Event() {
            @Override
            protected void execute(SimulationEngine e) {
                log.add("first");
                e.stop();
            }
        });
        engine.scheduleAt(2, new RecordingEvent("second", log, clock));

        engine.run();

        assertEquals(List.of("first"), log);
        assertEquals(1L, engine.now());
    }
}
