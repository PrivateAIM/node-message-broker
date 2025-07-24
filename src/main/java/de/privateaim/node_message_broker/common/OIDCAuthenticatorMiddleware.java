package de.privateaim.node_message_broker.common;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * A middleware for authenticating against several different services of a single external provider.
 */
@Slf4j
public final class OIDCAuthenticatorMiddleware implements ExchangeFilterFunction {

    private final Map<String, OIDCTokenPair> tokenPairByHost;
    private final OIDCAuthenticator oidcAuthenticator;

    public OIDCAuthenticatorMiddleware(OIDCAuthenticator oidcAuthenticator) {
        this.tokenPairByHost = new ConcurrentHashMap<>();
        this.oidcAuthenticator = requireNonNull(oidcAuthenticator, "OIDC authenticator must not be null");
    }

    @Override
    public @NonNull Mono<ClientResponse> filter(@NonNull ClientRequest request, ExchangeFunction next) {
        var host = request.url().getHost();
        if (host == null) {
            return Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST).build());
        }

        var authToken = computeAccessTokenForHost(host);
        var authenticatedRequest = ClientRequest.from(request)
                .headers(headers -> headers.setBearerAuth(authToken.getTokenValue()))
                .build();

        return next.exchange(authenticatedRequest).flatMap(response -> {
            // Handling for unauthorized events in case of time overlaps regarding token expiration.
            // Can happen if time skew is not properly handled by the authentication server.
            if (response.statusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                return response.releaseBody()
                        .then(Mono.just(computeAccessTokenForHost(host)))
                        .flatMap(token -> {
                            var newRequest = ClientRequest.from(request)
                                    .headers(headers -> headers.setBearerAuth(token.getTokenValue()))
                                    .build();
                            log.warn("retrying request to '{}' with new bearer token after receiving status code 401 " +
                                    "(unauthorized)", request.url());
                            return next.exchange(newRequest);
                        });
            } else {
                return Mono.just(response);
            }
        });
    }

    private OAuth2AccessToken computeAccessTokenForHost(@NonNull String host) {
        // TODO: revise - find another way to circumvent using block()
        // The following is an atomic operation!
        return tokenPairByHost.compute(host, (unused, tokenPair) -> {
            if (tokenPair == null) {
                log.info("acquiring access token for host '{}' as there is none yet", host);
                return oidcAuthenticator.authenticate().block();
            }

            if (tokenPair.accessToken().getExpiresAt().isBefore(Instant.now())) {
                return tokenPair.refreshToken()
                        .map(refreshToken -> {
                            if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
                                log.warn("refresh token expired - acquiring new pair of access token and refresh token for " +
                                        "host '{}'", host);
                                return oidcAuthenticator.authenticate().block();
                            } else {
                                log.info("refreshing access token for host '{}'", host);
                                return oidcAuthenticator.refresh(refreshToken).block();
                            }
                        })
                        .orElseGet(() -> oidcAuthenticator.authenticate().block());
            }
            return tokenPair;
        }).accessToken();
    }
}
