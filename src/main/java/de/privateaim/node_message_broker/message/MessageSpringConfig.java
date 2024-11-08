package de.privateaim.node_message_broker.message;

import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.common.hub.auth.HubAuthClient;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.HashMap;

@Slf4j
@Configuration
public class MessageSpringConfig {

    @Value("${app.hub.auth.robotId}")
    private String hubAuthRobotId;

    @Value("${app.hub.auth.robotSecret}")
    private String hubAuthRobotSecret;

    @Value("${app.hub.messenger.baseUrl}")
    private String hubMessengerBaseUrl;

    @Qualifier("HUB_MESSENGER_UNDERLYING_SOCKET")
    @Bean(destroyMethod = "disconnect")
    public Socket underlyingMessengerSocket(@Qualifier("HUB_AUTH_CLIENT") HubAuthClient hubAuthClient) {
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
}
