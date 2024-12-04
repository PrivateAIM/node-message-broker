package de.privateaim.node_message_broker.common.hub.auth;

import reactor.core.publisher.Mono;

/**
 * Describes a client able to carry out authentication related operations.
 */
public sealed interface HubAuthClient permits HttpHubAuthClient {

    /**
     * Asynchronously requests an access token from the hub using the "robot_credentials" grant type.
     *
     * @param clientId     the client's id
     * @param clientSecret the client's secret
     * @return The access token.
     */
    Mono<String> requestAccessToken(String clientId, String clientSecret);
}
