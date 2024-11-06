package de.privateaim.node_message_broker.common.hub.auth;

import reactor.core.publisher.Mono;

public sealed interface HubAuthClient permits HttpHubAuthClient {
    Mono<String> requestAccessToken(String clientId, String clientSecret);
}
