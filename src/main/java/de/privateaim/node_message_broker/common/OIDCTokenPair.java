package de.privateaim.node_message_broker.common;

import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

import java.util.Optional;

/**
 * An OIDC compliant pair of access token & refresh token.
 *
 * @param accessToken  JWT acting as an access token.
 * @param refreshToken JWT acting as a refresh token for acquiring a new access token.
 */
public record OIDCTokenPair(
        OAuth2AccessToken accessToken,
        Optional<OAuth2RefreshToken> refreshToken
) {
}
