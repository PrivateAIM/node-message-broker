package de.privateaim.node_message_broker.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.common.hub.auth.HubAuthClient;
import de.privateaim.node_message_broker.message.subscription.MessageSubscriptionService;
import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscriptionRepository;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Configuration
class MessageSpringConfig {

    @Value("${app.hub.auth.robotId}")
    private String hubAuthRobotId;

    @Value("${app.hub.auth.robotSecret}")
    private String hubAuthRobotSecret;

    @Value("${app.hub.messenger.baseUrl}")
    private String hubMessengerBaseUrl;

    private static final String SOCKET_RECEIVE_HUB_MESSAGE_IDENTIFIER = "send";


    @Qualifier("HUB_MESSENGER_UNDERLYING_SOCKET")
    @Bean(destroyMethod = "disconnect")
    public Socket underlyingMessengerSocket(@Qualifier("HUB_AUTH_CLIENT") HubAuthClient hubAuthClient,
                                            @Qualifier("HUB_MESSAGE_LISTENER") MessageListener messageListener) {
        IO.Options options = IO.Options.builder()
                .setPath(null)
                .setAuth(new HashMap<>())
                .build();

        final Socket socket = IO.socket(URI.create(hubMessengerBaseUrl), options);

        socket.on(Socket.EVENT_CONNECT_ERROR, objects -> {
            log.error("cannot connect to hub messenger at `{}`", hubMessengerBaseUrl);

            // we block here since this is a crucial component
            options.auth.put("token", hubAuthClient.requestAccessToken(hubAuthRobotId, hubAuthRobotSecret)
                    .block());

            log.info("reconnecting to hub messenger at `{}` with new authentication token", hubMessengerBaseUrl);
            socket.connect();
        });

        socket.on(Socket.EVENT_CONNECT,
                objects -> log.info("connected to hub messenger at `{}`", hubMessengerBaseUrl));

        socket.io().on(Manager.EVENT_RECONNECT_ATTEMPT,
                objects -> {
                    log.info("trying to reconnect to hub messenger via socket at `{}", hubMessengerBaseUrl);
                    // we block here since this is a crucial component
                    options.auth.put("token", hubAuthClient.requestAccessToken(hubAuthRobotId, hubAuthRobotSecret)
                            .block());
                });

        socket.on(SOCKET_RECEIVE_HUB_MESSAGE_IDENTIFIER, objects -> {
                    log.info("processing incoming message");
                    messageListener.onMessage(objects[0].toString().getBytes(StandardCharsets.UTF_8));
                }
        );

        socket.connect();
        return socket;
    }

    @Qualifier("HUB_MESSENGER_SOCKET")
    @Bean
    public MessageEmitter hubMessageSocket(@Qualifier("HUB_MESSENGER_UNDERLYING_SOCKET") Socket socket) {
        return new HubMessageEmitter(socket);
    }

    @Bean
    public MessageService messageService(@Qualifier("HUB_MESSENGER_SOCKET") MessageEmitter socket, HubClient hubClient) {
        return new MessageService(socket, hubClient);
    }

    @Qualifier("HUB_MESSAGE_FORWARD_WEB_CLIENT")
    @Bean
    public WebClient messageForwardWebClient() {
        return WebClient.builder()
                .defaultHeaders(httpHeaders -> httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .build();
    }


    @Bean
    MessageSubscriptionService messageSubscriptionService(MessageSubscriptionRepository messageSubscriptionRepository) {
        return new MessageSubscriptionService(messageSubscriptionRepository);
    }

    @Qualifier("HUB_MESSAGE_JSON_MAPPER")
    @Bean
    ObjectMapper hubMessageJsonMapper() {
        return new ObjectMapper();
    }

    @Qualifier("HUB_MESSAGE_CONSUMER_FORWARD")
    @Bean
    MessageConsumer hubMessageConsumer(@Qualifier("HUB_MESSAGE_FORWARD_WEB_CLIENT") WebClient webClient,
                                       MessageSubscriptionService messageSubscriptionService) {
        return new HubMessageForwarder(webClient, messageSubscriptionService);
    }

    @Qualifier("HUB_MESSAGE_LISTENER")
    @Bean
    MessageListener hubMessageListener(@Qualifier("HUB_MESSAGE_CONSUMER_FORWARD") MessageConsumer hubMessageConsumer,
                                       @Qualifier("HUB_MESSAGE_JSON_MAPPER") ObjectMapper jsonMapper) {
        return new HubMessageListener(hubMessageConsumer, jsonMapper);
    }
}
