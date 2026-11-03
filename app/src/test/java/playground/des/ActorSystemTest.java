package playground.des;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActorSystemTest {

    private record Request() implements Message {}

    private record Response() implements Message {}

    @Test
    void messageRoundTripWithModelledServiceTime() {
        ActorSystem system = new ActorSystem();

        long serviceTime = 7L;

        // Server replies after a modelled service time.
        class Server extends Actor {
            long receivedAt = -1;

            Server() {
                super("server");
            }

            @Override
            protected void receive(Message message, ActorContext context) {
                receivedAt = context.now();
                context.send(context.sender(), new Response(), serviceTime);
            }
        }

        Server server = new Server();

        class Client extends Actor {
            long repliedAt = -1;
            Message got;

            Client() {
                super("client");
            }

            @Override
            protected void receive(Message message, ActorContext context) {
                if (message instanceof Response) {
                    repliedAt = context.now();
                    got = message;
                    context.stop();
                } else {
                    context.send(server, message);
                }
            }
        }

        Client client = new Client();

        // Kick off: deliver a Request to the client at t=0, which forwards to the server.
        system.tell(client, new Request(), 0);
        system.run();

        assertEquals(0L, server.receivedAt);
        assertEquals(serviceTime, client.repliedAt);
        assertTrue(client.got instanceof Response);
        assertEquals(serviceTime, system.now());
    }

    @Test
    void replyRoutesToSender() {
        ActorSystem system = new ActorSystem();

        class Echo extends Actor {
            Echo() {
                super("echo");
            }

            @Override
            protected void receive(Message message, ActorContext context) {
                context.reply(new Response());
            }
        }

        Echo echo = new Echo();

        class Caller extends Actor {
            Actor replySelf;
            Actor replySender;

            Caller() {
                super("caller");
            }

            @Override
            protected void receive(Message message, ActorContext context) {
                if (message instanceof Response) {
                    replySelf = context.self();
                    replySender = context.sender();
                } else {
                    context.send(echo, message);
                }
            }
        }

        Caller caller = new Caller();

        system.tell(caller, new Request(), 0);
        system.run();

        // The reply came back to the caller, and its sender is the echo actor.
        assertSame(caller, caller.replySelf);
        assertSame(echo, caller.replySender);
    }

    @Test
    void externallyInjectedMessageHasNullSender() {
        ActorSystem system = new ActorSystem();

        class Sink extends Actor {
            Actor observedSender;
            boolean received;

            Sink() {
                super("sink");
            }

            @Override
            protected void receive(Message message, ActorContext context) {
                received = true;
                observedSender = context.sender();
                assertSame(this, context.self());
            }
        }

        Sink sink = new Sink();

        system.tell(sink, new Request(), 3);
        system.run();

        assertTrue(sink.received);
        assertNull(sink.observedSender);
        assertEquals(3L, system.now());
    }
}
