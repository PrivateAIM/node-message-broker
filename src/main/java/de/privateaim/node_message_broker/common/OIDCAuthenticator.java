package de.privateaim.node_message_broker.common;

import org.springframework.security.oauth2.core.OAuth2Token;
import reactor.core.publisher.Mono;

/**
 * Describes an OIDC compliant authenticator.
 */
public interface OIDCAuthenticator {
    /**
     * Authenticates against an external system.
     *
     * @return A pair of access and refresh token.
     */
    Mono<OIDCTokenPair> authenticate();

    /**
     * Refreshes the authentication against an external system.
     *
     * @param refreshToken The refresh token to be used.
     * @return A pair of access and refresh token.
     */
    Mono<OIDCTokenPair> refresh(OAuth2Token refreshToken);
}
