package de.privateaim.node_message_broker.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.privateaim.node_message_broker.ConfigurationUtil;
import de.privateaim.node_message_broker.common.OIDCAuthenticator;
import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.message.crypto.HubMessageCryptoService;
import de.privateaim.node_message_broker.message.crypto.MessageCryptoService;
import de.privateaim.node_message_broker.message.emit.EmitMessage;
import de.privateaim.node_message_broker.message.emit.HubMessageEmitter;
import de.privateaim.node_message_broker.message.emit.HubMessageEncryptionMiddleware;
import de.privateaim.node_message_broker.message.emit.MessageEmitter;
import de.privateaim.node_message_broker.message.receive.*;
import de.privateaim.node_message_broker.message.subscription.MessageSubscriptionService;
import de.privateaim.node_message_broker.message.subscription.MessageSubscriptionServiceImpl;
import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscriptionRepository;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

@Slf4j
@Configuration
class MessageSpringConfig {
    @Value("${app.hub.messenger.baseUrl}")
    private String hubMessengerBaseUrl;

    @Value("${app.security.nodePrivateECDHKeyFile}")
    private String nodePrivateECDHKeyFile;

    @Value("${app.hub.auth.robotId}")
    private String selfRobotId;

    @Value("${app.proxy.host}")
    private String proxyHost;

    @Value("${app.proxy.port}")
    private Integer proxyPort;

    @Value("${app.proxy.whitelist}")
    private String proxyWhitelist;

    @Value("${app.proxy.username}")
    private String proxyUsername;

    @Value("${app.proxy.passwordFile}")
    private String proxyPasswordFile;

    private static final String SOCKET_RECEIVE_HUB_MESSAGE_IDENTIFIER = "send";


    @Qualifier("HUB_MESSENGER_UNDERLYING_SOCKET_SECURE_CLIENT")
    @Bean
    OkHttpClient decoratedSocketBaseClient(@Qualifier("COMMON_JAVA_SSL_CONTEXT") SSLContext sslCtx,
                                           @Qualifier("COMMON_TRUST_MANAGER_FACTORY") TrustManagerFactory tmf) {

        var clientBuilder = new OkHttpClient.Builder()
                .readTimeout(1, TimeUnit.MINUTES);
        decorateClientWithSSLContext(clientBuilder, sslCtx, tmf);
        decorateClientWithProxySettings(clientBuilder);

        return clientBuilder.build();
    }


    // Additional SSL configuration that's orthogonal to the one used in the core HTTP client.
    // The reason for that is that socket.io decided to go with a specific HTTP client instead of using an interface.
    // Hence, we're bound to using that client which also comes with a specific way of configuring it.
    private void decorateClientWithSSLContext(OkHttpClient.Builder clientBuilder, SSLContext sslCtx,
                                              TrustManagerFactory tmf) {
        clientBuilder.sslSocketFactory(sslCtx.getSocketFactory(), (X509TrustManager) tmf.getTrustManagers()[0]);
    }

    // Additional proxy configuration that's orthogonal to the one used in the core HTTP client.
    // The reason for that is that socket.io decided to go with a specific HTTP client instead of using an interface.
    // Hence, we're bound to using that client which also comes with a specific way of configuring it.
    private void decorateClientWithProxySettings(OkHttpClient.Builder clientBuilder) {
        var proxyWhitelistPattern = Pattern.compile(proxyWhitelist);
        if (proxyWhitelistPattern.matcher(proxyHost).matches()) {
            log.warn("skipping proxy configuration for message socket due to the host `{}` matching the proxy " +
                    "whitelist with pattern `{}`", proxyHost, proxyWhitelistPattern);
            return;
        }

        log.info("configuring usage of proxy for message socket at `{}:{}`", proxyHost, proxyPort);
        var proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
        clientBuilder.proxy(proxy);

        if (proxyUsername != null && proxyPasswordFile != null) {
            try {
                var proxyPassword = Files.readString(Paths.get(proxyPasswordFile));

                log.info("configuring authentication for proxy of message socket");
                clientBuilder.proxyAuthenticator((route, response) -> {
                    var proxyCredentials = Credentials.basic(proxyUsername, proxyPassword);
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", proxyCredentials)
                            .build();
                });
            } catch (IOException e) {
                log.error("cannot read password file for proxy at `{}`", proxyPasswordFile, e);
                throw new RuntimeException(e);
            }
        }
    }


    @Qualifier("HUB_MESSENGER_UNDERLYING_SOCKET")
    @Bean(destroyMethod = "disconnect")
    public Socket underlyingMessengerSocket(
            @Qualifier("HUB_AUTHENTICATOR") OIDCAuthenticator hubAuthenticator,
            @Qualifier("HUB_MESSAGE_RECEIVER") MessageReceiver messageReceiver,
            @Qualifier("HUB_MESSENGER_UNDERLYING_SOCKET_SECURE_CLIENT") OkHttpClient secureBaseClient) {
        IO.Options options = IO.Options.builder()
                .setPath(null)
                .setAuth(new HashMap<>())
                .build();

        // this is used for SSL backed connections that need to trust additional certificates
        options.callFactory = secureBaseClient;
        options.webSocketFactory = secureBaseClient;

        final Socket socket = IO.socket(URI.create(hubMessengerBaseUrl), options);

        socket.on(Socket.EVENT_CONNECT_ERROR, objects -> {
            log.error("cannot connect to hub messenger at `{}`", hubMessengerBaseUrl);

            // we block here since this is a crucial component
            var oidcTokenPair = hubAuthenticator.authenticate().block();
            if (oidcTokenPair == null) {
                throw new RuntimeException("authentication failed - cannot connect to hub messenger at `%s`"
                        .formatted(hubMessengerBaseUrl));
            }
            options.auth.put("token", oidcTokenPair.accessToken().getTokenValue());

            log.info("reconnecting to hub messenger at `{}` with new authentication token", hubMessengerBaseUrl);
            socket.connect();
        });

        socket.on(Socket.EVENT_CONNECT,
                objects -> log.info("connected to hub messenger at `{}`", hubMessengerBaseUrl));

        socket.io().on(Manager.EVENT_RECONNECT_ATTEMPT,
                objects -> {
                    log.info("trying to reconnect to hub messenger via socket at `{}", hubMessengerBaseUrl);
                    // we block here since this is a crucial component
                    var oidcTokenPair = hubAuthenticator.authenticate().block();
                    if (oidcTokenPair == null) {
                        throw new RuntimeException("authentication failed - cannot connect to hub messenger at `%s`"
                                .formatted(hubMessengerBaseUrl));
                    }

                    options.auth.put("token", oidcTokenPair.accessToken().getTokenValue());
                });

        socket.on(SOCKET_RECEIVE_HUB_MESSAGE_IDENTIFIER, objects -> {
                    log.debug("processing incoming message");
                    messageReceiver.processMessage(objects[0].toString().getBytes(StandardCharsets.UTF_8))
                            .doOnError(err -> log.error("failed to process incoming message", err))
                            .subscribe();
                }
        );

        socket.connect();
        return socket;
    }


    @Bean
    MessageCryptoService hubMessageCryptoService() {
        return new HubMessageCryptoService(new SecureRandom());
    }

    @Qualifier("NODE_SECURITY_PRIVATE_ECDH_KEY")
    @Bean
    ECPrivateKey nodePrivateKey() throws IOException {
        var nodePrivateECDHKeyContent = ConfigurationUtil.readExternalFileContent(nodePrivateECDHKeyFile);

        try {
            try (var privateECDHKeyReader = new InputStreamReader(new ByteArrayInputStream(nodePrivateECDHKeyContent))) {
                var pemParser = new PEMParser(privateECDHKeyReader);
                var object = pemParser.readObject();

                var converter = new JcaPEMKeyConverter();
                if (object instanceof PrivateKeyInfo) {
                    var privateKeyInfo = PrivateKeyInfo.getInstance(object);
                    return (ECPrivateKey) converter.getPrivateKey(privateKeyInfo);
                } else if (object instanceof PEMKeyPair) {
                    return (ECPrivateKey) converter.getPrivateKey(((PEMKeyPair) object).getPrivateKeyInfo());
                } else {
                    throw new RuntimeException("unexpected key type");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("cannot read node's private ECDH key", e);
        }
    }

    @Qualifier("HUB_MESSAGE_EMIT_KDF_KEYING_INFO_GEN")
    @Bean
    Function<EmitMessage, byte[]> hubMessageEmitKDFKeyingInfoGenerator() {
        return (message) -> (message.context().messageId() + message.context().analysisId()).getBytes();
    }

    @Qualifier("HUB_MESSAGE_RECEIVE_KDF_KEYING_INFO_GEN")
    @Bean
    Function<ReceiveMessage, byte[]> hubMessageReceiveKDFKeyingInfoGenerator() {
        return (message) -> (message.context().messageId() + message.context().analysisId()).getBytes();
    }

    @Qualifier("HUB_MESSAGE_EMIT_MIDDLEWARE_ENCRYPT")
    @Bean
    Function<EmitMessage, Mono<EmitMessage>> hubMessageEmitEncryptionMiddleware(
            @Qualifier("NODE_SECURITY_PRIVATE_ECDH_KEY") ECPrivateKey nodePrivateKey,
            MessageCryptoService messageCryptoService,
            HubClient hubClient,
            @Qualifier("HUB_MESSAGE_EMIT_KDF_KEYING_INFO_GEN") Function<EmitMessage, byte[]> kdfKeyingInfoGenerator
    ) {
        return new HubMessageEncryptionMiddleware(
                nodePrivateKey,
                messageCryptoService,
                hubClient,
                kdfKeyingInfoGenerator
        );
    }

    @Qualifier("HUB_MESSAGE_EMIT_MIDDLEWARE_BASE64_ENCODE")
    @Bean
    Function<EmitMessage, Mono<EmitMessage>> hubMessageBase64EncodingMiddleware() {
        var b64Encoder = Base64.getEncoder();

        return message -> Mono.just(new EmitMessage(
                message.recipient(),
                b64Encoder.encode(message.payload()),
                message.context()
        ));
    }

    @Qualifier("HUB_MESSAGE_EMIT_MIDDLEWARES")
    @Bean
    public List<Function<EmitMessage, Mono<EmitMessage>>> hubMessageEmitMiddlewares(
            @Qualifier("HUB_MESSAGE_EMIT_MIDDLEWARE_ENCRYPT") Function<EmitMessage, Mono<EmitMessage>> encryptMiddleware,
            @Qualifier("HUB_MESSAGE_EMIT_MIDDLEWARE_BASE64_ENCODE") Function<EmitMessage, Mono<EmitMessage>> base64EncodeMiddleware
    ) {
        return List.of(encryptMiddleware,
                base64EncodeMiddleware);
    }

    @Qualifier("HUB_MESSENGER_SOCKET")
    @Bean
    public MessageEmitter<EmitMessage> hubMessageSocket(
            @Qualifier("HUB_MESSENGER_UNDERLYING_SOCKET") Socket socket,
            @Qualifier("HUB_MESSAGE_EMIT_MIDDLEWARES") List<Function<EmitMessage, Mono<EmitMessage>>> middlewares
    ) {
        var hubMessageEmitter = new HubMessageEmitter(socket);
        middlewares.forEach(hubMessageEmitter::registerMiddleware);
        return hubMessageEmitter;
    }

    @Bean
    public MessageService messageService(
            @Qualifier("HUB_MESSENGER_SOCKET") MessageEmitter<EmitMessage> socket,
            HubClient hubClient) {
        return new MessageService(socket, hubClient, selfRobotId);
    }

    @Qualifier("HUB_MESSAGE_RECEIVE_FORWARD_WEB_CLIENT")
    @Bean
    public WebClient messageForwardWebClient(
            @Qualifier("CORE_HTTP_CONNECTOR") ReactorClientHttpConnector httpConnector) {
        return WebClient.builder()
                .defaultHeaders(httpHeaders -> httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .clientConnector(httpConnector)
                .build();
    }

    @Bean
    MessageSubscriptionService messageSubscriptionService(
            MessageSubscriptionRepository messageSubscriptionRepository) {
        return new MessageSubscriptionServiceImpl(messageSubscriptionRepository);
    }

    @Qualifier("HUB_MESSAGE_RECEIVE_MIDDLEWARE_DECRYPT")
    @Bean
    Function<ReceiveMessage, Mono<ReceiveMessage>> hubMessageReceiveDecryptionMiddleware(
            @Qualifier("NODE_SECURITY_PRIVATE_ECDH_KEY") ECPrivateKey nodePrivateKey,
            MessageCryptoService messageCryptoService,
            HubClient hubClient,
            @Qualifier("HUB_MESSAGE_RECEIVE_KDF_KEYING_INFO_GEN") Function<ReceiveMessage, byte[]> kdfKeyingInfoGenerator
    ) {
        return new HubMessageDecryptionMiddleware(
                nodePrivateKey,
                messageCryptoService,
                hubClient,
                kdfKeyingInfoGenerator
        );
    }

    @Qualifier("HUB_MESSAGE_RECEIVE_MIDDLEWARE_BASE64_DECODE")
    @Bean
    Function<ReceiveMessage, Mono<ReceiveMessage>> hubMessageBase64DecodingMiddleware() {
        var b64Encoder = Base64.getDecoder();

        return message -> Mono.just(new ReceiveMessage(
                message.sender(),
                b64Encoder.decode(message.payload()),
                message.context()
        ));
    }

    @Qualifier("HUB_MESSAGE_RECEIVE_MIDDLEWARES")
    @Bean
    List<Function<ReceiveMessage, Mono<ReceiveMessage>>> hubMessageReceiveMiddlewares(
            @Qualifier("HUB_MESSAGE_RECEIVE_MIDDLEWARE_BASE64_DECODE") Function<ReceiveMessage, Mono<ReceiveMessage>> base64DecodeMiddleware,
            @Qualifier("HUB_MESSAGE_RECEIVE_MIDDLEWARE_DECRYPT") Function<ReceiveMessage, Mono<ReceiveMessage>> decryptMiddleware
    ) {
        return List.of(
                base64DecodeMiddleware,
                decryptMiddleware);
    }

    @Qualifier("HUB_MESSAGE_RECEIVE_CONSUMER")
    @Bean
    MessageConsumer hubMessageConsumer(
            @Qualifier("HUB_MESSAGE_RECEIVE_FORWARD_WEB_CLIENT") WebClient webClient,
            MessageSubscriptionService messageSubscriptionService
    ) {
        var config = new HubMessageWebhookSubscriptionForwarderConfig.Builder()
                .withMaxRetries(5)
                .withRetryDelayMs(1000)
                .build();

        return new HubMessageWebhookSubscriptionForwarder(webClient, messageSubscriptionService, config);
    }

    @Qualifier("HUB_MESSAGE_RECEIVER")
    @Bean
    MessageReceiver hubMessageReceiver(
            @Qualifier("HUB_JSON_MAPPER") ObjectMapper jsonMapper,
            @Qualifier("HUB_MESSAGE_RECEIVE_MIDDLEWARES") List<Function<ReceiveMessage, Mono<ReceiveMessage>>> middlewares,
            @Qualifier("HUB_MESSAGE_RECEIVE_CONSUMER") MessageConsumer messageConsumer
    ) {
        var messageReceiver = new HubMessageReceiver(jsonMapper);
        middlewares.forEach(messageReceiver::registerMiddleware);
        messageReceiver.registerConsumer(messageConsumer);

        return messageReceiver;
    }
}
