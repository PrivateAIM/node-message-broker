package de.privateaim.node_message_broker.message;

import java.security.KeyStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import okhttp3.OkHttpClient;

public final class MessageSpringConfigTest {
    private static final int HUB_MESSENGER_PING_INTERVAL_MS = 25_000;

    private MessageSpringConfig config;
    private SSLContext sslContext;
    private TrustManagerFactory trustManagerFactory;

    @BeforeEach
    public void setUp() throws Exception {
        config = new MessageSpringConfig();
        ReflectionTestUtils.setField(config, "proxyUrl", "");
        ReflectionTestUtils.setField(config, "proxyWhitelist", "");

        sslContext = SSLContext.getDefault();
        trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
    }

    @Test
    public void socketClientReadTimeoutOutlastsHubMessengerPingInterval() {
        OkHttpClient client = config.decoratedSocketBaseClient(sslContext, trustManagerFactory);

        assertTrue(client.readTimeoutMillis() > HUB_MESSENGER_PING_INTERVAL_MS,
                "read timeout of " + client.readTimeoutMillis() + "ms aborts the long-poll before the hub "
                        + "messenger sends its ping at " + HUB_MESSENGER_PING_INTERVAL_MS + "ms, which kills "
                        + "the connection on every cycle whenever the websocket upgrade is unavailable");
    }
}
