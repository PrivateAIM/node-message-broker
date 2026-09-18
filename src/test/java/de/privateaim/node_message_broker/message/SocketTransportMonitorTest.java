package de.privateaim.node_message_broker.message;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.socket.client.Manager;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.Transport;
import io.socket.engineio.parser.Packet;

public final class SocketTransportMonitorTest {

    private static final String MESSENGER_URL = "https://messenger.staging.privateaim.net";
    private static final String PROXY_URL = "http://someuser:hunter2@192.168.178.83:3128";

    private List<String> warnings;
    private SocketTransportMonitor monitor;
    private Emitter manager;

    @BeforeEach
    public void setUp() {
        warnings = new ArrayList<>();
        monitor = new SocketTransportMonitor(MESSENGER_URL, PROXY_URL, warnings::add);
        manager = new Emitter();
        monitor.bindTo(manager);
    }

    @Test
    public void reportsPollingTransportAsDegraded() {
        openTransport("polling");

        assertTrue(monitor.degraded());
    }

    @Test
    public void reportsWebsocketTransportAsHealthy() {
        openTransport("websocket");

        assertFalse(monitor.degraded());
    }

    @Test
    public void ignoresUpgradeProbesThatNeverOpen() {
        openTransport("polling");

        // A blocked upgrade still creates a transport; it just never opens.
        createTransport("websocket");

        assertTrue(monitor.degraded());
    }

    @Test
    public void warnsWhenTheWebsocketUpgradeProbeFails() {
        openTransport("polling");

        failTransport("websocket");

        assertEquals(1, warnings.size());
    }

    @Test
    public void warnsOnceAcrossRepeatedFailedProbes() {
        openTransport("polling");

        failTransport("websocket");
        failTransport("websocket");

        assertEquals(1, warnings.size());
    }

    @Test
    public void warnsAgainAfterRecoveringAndDegradingOnceMore() {
        openTransport("polling");
        failTransport("websocket");

        openTransport("websocket");

        openTransport("polling");
        failTransport("websocket");

        assertEquals(2, warnings.size());
    }

    @Test
    public void doesNotWarnWhenAnEstablishedWebsocketLaterDrops() {
        var websocket = openTransport("websocket");

        websocket.emit(Transport.EVENT_ERROR, new RuntimeException("connection reset"));

        assertEquals(List.of(), warnings);
    }

    @Test
    public void doesNotWarnWhenThePollingTransportItselfErrors() {
        openTransport("polling");

        failTransport("polling");

        assertEquals(List.of(), warnings);
    }

    @Test
    public void warningDoesNotLeakProxyCredentials() {
        openTransport("polling");

        failTransport("websocket");

        var warning = warnings.getFirst();
        assertFalse(warning.contains("hunter2"), "warning must not leak proxy credentials, but was: " + warning);
        assertFalse(warning.contains("someuser"), "warning must not leak proxy credentials, but was: " + warning);
    }

    private FakeTransport createTransport(String name) {
        var transport = new FakeTransport(name);
        manager.emit(Manager.EVENT_TRANSPORT, transport);
        return transport;
    }

    private FakeTransport openTransport(String name) {
        var transport = createTransport(name);
        transport.emit(Transport.EVENT_OPEN);
        return transport;
    }

    private FakeTransport failTransport(String name) {
        var transport = createTransport(name);
        transport.emit(Transport.EVENT_ERROR, new RuntimeException("blocked"));
        return transport;
    }

    private static final class FakeTransport extends Transport {

        private FakeTransport(String name) {
            super(new Transport.Options());
            this.name = name;
        }

        @Override
        protected void write(Packet[] packets) {
        }

        @Override
        protected void doOpen() {
        }

        @Override
        protected void doClose() {
        }
    }
}
