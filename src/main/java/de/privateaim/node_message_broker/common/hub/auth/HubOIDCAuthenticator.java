package de.privateaim.node_message_broker.common.hub.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.privateaim.node_message_broker.common.HttpRetryConfig;
import de.privateaim.node_message_broker.common.OIDCAuthenticator;
import de.privateaim.node_message_broker.common.OIDCTokenPair;
import de.privateaim.node_message_broker.common.hub.auth.api.HubAuthTokenResponse;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * An OIDC compliant authenticator for authenticating against the Hub.
 */
@Slf4j
public final class HubOIDCAuthenticator implements OIDCAuthenticator {

    private final WebClient webClient;
    private final HttpRetryConfig retryConfig;
    private final ObjectMapper jsonDecoder;
    private final String clientId;
    private final String clientSecret;

    private static final String TOKEN_PATH = "/token";
    private static final String GRANT_TYPE_AUTHENTICATE = "robot_credentials";
    private static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    private static final String JWT_CLAIM__ISSUED_AT = "iat";
    private static final String JWT_CLAIM__EXPIRES_AT = "exp";

    private HubOIDCAuthenticator(Builder builder) {
        this.webClient = requireNonNull(builder.getWebClient(), "web client must not be null");
        this.retryConfig = requireNonNull(builder.getRetryConfig(), "retry configuration must not be null");
        this.jsonDecoder = requireNonNull(builder.getJsonDecoder(), "json decoder must not be null");
        this.clientId = requireNonNull(builder.getClientId(), "client ID must not be null");
        this.clientSecret = requireNonNull(builder.getClientSecret(), "client secret must not be null");
    }

    @Override
    public Mono<OIDCTokenPair> authenticate() {
        return webClient.post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData("grant_type", GRANT_TYPE_AUTHENTICATE)
                        .with("id", clientId)
                        .with("secret", clientSecret))
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError,
                        response -> Mono.error(new HubAuthException("could not fetch hub access token")))
                .bodyToMono(HubAuthTokenResponse.class)
                .flatMap(hubAuthTokenResponse -> {
                    try {
                        return Mono.just(new OIDCTokenPair(
                                parseAccessToken(hubAuthTokenResponse.accessToken),
                                Optional.ofNullable(parseRefreshToken(hubAuthTokenResponse.refreshToken))));
                    } catch (IllegalArgumentException e) {
                        return Mono.error(
                                new HubAuthMalformedResponseException("received malformed response from HUB when " +
                                        "trying to fetch access token", e));
                    }
                })
                .doOnError(err -> {
                    log.error("authentication request failed: {}", err.getMessage());
                    log.debug("authentication request error details", err);
                })
                .retryWhen(Retry.backoff(retryConfig.maxRetries(), Duration.ofMillis(retryConfig.retryDelayMs()))
                        .jitter(0.75)
                        .filter(err -> err instanceof HubAuthException)
                        .onRetryExhaustedThrow(((retryBackoffSpec,
                                retrySignal) -> new HubAccessTokenNotObtainable("exhausted maximum retries of '%d'"
                                        .formatted(retryConfig.maxRetries())))));
    }

    @Override
    public Mono<OIDCTokenPair> refresh(OAuth2Token refreshToken) {
        return webClient.post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData("grant_type", GRANT_TYPE_REFRESH_TOKEN)
                        .with("refresh_token", refreshToken.getTokenValue()))
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError,
                        response -> Mono
                                .error(new HubAuthException("could not fetch new access token using refresh token")))
                .bodyToMono(HubAuthTokenResponse.class)
                .flatMap(hubAuthTokenResponse -> {
                    try {
                        return Mono.just(new OIDCTokenPair(
                                parseAccessToken(hubAuthTokenResponse.accessToken),
                                Optional.ofNullable(parseRefreshToken(hubAuthTokenResponse.refreshToken))));
                    } catch (IllegalArgumentException e) {
                        return Mono.error(
                                new HubAuthMalformedResponseException("received malformed response from HUB when " +
                                        "trying to refresh access token", e));
                    }
                })
                .doOnError(err -> {
                    log.error("token refresh request failed: {}", err.getMessage());
                    log.debug("token refresh error details", err);
                })
                .retryWhen(Retry.backoff(retryConfig.maxRetries(), Duration.ofMillis(retryConfig.retryDelayMs()))
                        .jitter(0.75)
                        .filter(err -> err instanceof HubAuthException)
                        .onRetryExhaustedThrow(((retryBackoffSpec,
                                retrySignal) -> new HubAccessTokenNotObtainable("exhausted maximum retries of '%d'"
                                        .formatted(retryConfig.maxRetries())))));
    }

    private OAuth2AccessToken parseAccessToken(@NonNull String tokenValue) {
        try {
            var jwt = decodeJwt(tokenValue);
            if (jwt.getIssuedAt() == null) {
                throw new IllegalArgumentException("JWT is missing issuing claim");
            }
            if (jwt.getExpiresAt() == null) {
                throw new IllegalArgumentException("JWT is missing expiration claim");
            }

            return new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    jwt.getTokenValue(),
                    jwt.getIssuedAt(),
                    jwt.getExpiresAt());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to decode access token from Hub");
        }
    }

    private OAuth2RefreshToken parseRefreshToken(String tokenValue) {
        if (tokenValue == null) {
            return null;
        }

        try {
            var jwt = decodeJwt(tokenValue);
            if (jwt.getIssuedAt() == null) {
                throw new IllegalArgumentException("JWT is missing issuing claim");
            }
            if (jwt.getExpiresAt() == null) {
                throw new IllegalArgumentException("JWT is missing expiration claim");
            }

            return new OAuth2RefreshToken(
                    jwt.getTokenValue(),
                    jwt.getIssuedAt(),
                    jwt.getExpiresAt());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to decode refresh token from Hub");
        }
    }

    private OAuth2Token decodeJwt(@NonNull String tokenValue) throws JsonProcessingException {
        var chunks = tokenValue.split("\\.");

        if (chunks.length < 2) {
            throw new IllegalArgumentException("could not decode JWT due to missing payload section");
        }
        var payload = new String(Base64.getUrlDecoder().decode(chunks[1]));

        var payloadJson = jsonDecoder.readTree(payload);

        var issuedAt = Optional.ofNullable(payloadJson.get(JWT_CLAIM__ISSUED_AT))
                .map(JsonNode::asLong)
                .map(Instant::ofEpochSecond)
                .orElse(null);
        var expiresAt = Optional.ofNullable(payloadJson.get(JWT_CLAIM__EXPIRES_AT))
                .map(JsonNode::asLong)
                .map(Instant::ofEpochSecond)
                .orElse(null);

        return new HubJWT(tokenValue, issuedAt, expiresAt);
    }

    private record HubJWT(String tokenValue, Instant issuedAt, Instant expiresAt) implements OAuth2Token {
        @Override
        public String getTokenValue() {
            return tokenValue;
        }

        @Override
        public Instant getIssuedAt() {
            return issuedAt;
        }

        @Override
        public Instant getExpiresAt() {
            return expiresAt;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Getter
    public static class Builder {
        private WebClient webClient;
        private HttpRetryConfig retryConfig;
        private ObjectMapper jsonDecoder;
        private String clientId;
        private String clientSecret;

        Builder() {
        }

        public Builder usingWebClient(WebClient webClient) {
            this.webClient = webClient;
            return this;
        }

        public Builder withRetryConfig(HttpRetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        public Builder withJsonDecoder(ObjectMapper jsonDecoder) {
            this.jsonDecoder = jsonDecoder;
            return this;
        }

        public Builder withAuthCredentials(String clientId, String clientSecret) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            return this;
        }

        public OIDCAuthenticator build() {
            // TODO: add validation step
            return new HubOIDCAuthenticator(this);
        }
    }
}
