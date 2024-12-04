package de.privateaim.node_message_broker.common.hub.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * A client for communicating with the hub's auth service via HTTP/HTTPS.
 */
@Slf4j
public final class HttpHubAuthClient implements HubAuthClient {

    private final WebClient webClient;
    private final HttpHubAuthClientConfig config;

    private static final String TOKEN_PATH = "/token";
    private static final String GRANT_TYPE = "robot_credentials";

    public HttpHubAuthClient(WebClient webClient, HttpHubAuthClientConfig config) {
        this.webClient = requireNonNull(webClient, "web client must not be null");
        this.config = requireNonNull(config, "config must not be null");
    }

    @Override
    public Mono<String> requestAccessToken(String clientId, String clientSecret) {
        return webClient.post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData("grant_type", GRANT_TYPE)
                        .with("id", clientId)
                        .with("secret", clientSecret))
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError,
                        response -> {
                            var err = new HubAuthException("could not fetch hub access token");

                            log.warn("retrying token request after failed attempt", err);
                            return Mono.error(err);
                        })
                .bodyToMono(HubAuthTokenResponse.class)
                .map(hubAuthTokenResponse -> hubAuthTokenResponse.accessToken)
                .retryWhen(Retry.backoff(config.maxRetries(), Duration.ofMillis(config.retryDelayMs()))
                        .jitter(0.75)
                        .filter(err -> err instanceof HubAuthException)
                        .onRetryExhaustedThrow(((retryBackoffSpec, retrySignal) ->
                                new HubAccessTokenNotObtainable("exhausted maximum retries of '%d'"
                                        .formatted(config.maxRetries())))));
    }
}
