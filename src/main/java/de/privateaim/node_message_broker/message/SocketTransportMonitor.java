package de.privateaim.node_message_broker.message;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.socket.client.Manager;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.Transport;

/** Tracks the engine.io transport in use and reports when the socket cannot upgrade off HTTP long-polling. */
public final class SocketTransportMonitor {

    private static final String TRANSPORT_WEBSOCKET = "websocket";

    private final String messengerHost;
    private final String proxyDescription;
    private final Consumer<String> warningSink;
    private final AtomicBoolean warned = new AtomicBoolean();

    private volatile String transport;

    /** {@code proxyUrl} may carry credentials; only its host and port are ever reported. */
    public SocketTransportMonitor(String messengerBaseUrl, String proxyUrl, Consumer<String> warningSink) {
        this.messengerHost = hostOf(messengerBaseUrl);
        this.proxyDescription = describeProxy(proxyUrl);
        this.warningSink = warningSink;
    }

    public void bindTo(Emitter manager) {
        manager.on(Manager.EVENT_TRANSPORT, objects -> {
            if (objects.length == 0 || !(objects[0] instanceof Transport created)) {
                return;
            }

            var opened = new AtomicBoolean();
            created.on(Transport.EVENT_OPEN, ignored -> {
                opened.set(true);
                recordTransport(created.name);
            });

            created.on(Transport.EVENT_ERROR, ignored -> {
                if (TRANSPORT_WEBSOCKET.equals(created.name) && !opened.get()) {
                    reportDegraded();
                }
            });
        });
    }

    public boolean degraded() {
        var current = transport;
        return current != null && !TRANSPORT_WEBSOCKET.equals(current);
    }

    private void recordTransport(String name) {
        transport = name;
        if (TRANSPORT_WEBSOCKET.equals(name)) {
            warned.set(false);
        }
    }

    private void reportDegraded() {
        if (!degraded() || !warned.compareAndSet(false, true)) {
            return;
        }

        warningSink.accept("hub messenger socket restricted to HTTP long-polling because the websocket upgrade to `"
                + messengerHost + "` was rejected" + (proxyDescription == null ? ""
                : ", most likely by the proxy at `" + proxyDescription + "`")
                + ". Messaging works, but will be slower and reconnects often. To enable websockets, the proxy "
                + "must forward the `Upgrade: websocket` header.");
    }

    private static String describeProxy(String proxyUrl) {
        var host = hostOf(proxyUrl);
        if (host == null) {
            return null;
        }

        var port = URI.create(proxyUrl).getPort();
        return port == -1 ? host : host + ":" + port;
    }

    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
