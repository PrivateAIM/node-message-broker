package de.privateaim.node_message_broker.common.hub.auth;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import static java.util.Objects.requireNonNull;

/**
 * Filter for a {@link org.springframework.web.reactive.function.client.WebClient} which handles re-authentication of
 * the request if the request target responds with a 401 (Unauthorized) status code.
 */
@Slf4j
public class RenewAuthTokenFilter implements ExchangeFilterFunction {

    private final HubAuthClient authClient;

    private final String robotId;

    private final String robotSecret;

    /**
     * Creates a new instance of the filter with information required for re-authenticating any in-flight requests.
     *
     * @param authClient  client able to communicate to the Hub's auth service
     * @param robotId     client's robot id used for authentication
     * @param robotSecret client's robot secret used for authentication
     */
    public RenewAuthTokenFilter(HubAuthClient authClient, String robotId, String robotSecret) {
        this.authClient = requireNonNull(authClient, "auth client must not be null");
        this.robotId = robotId;
        this.robotSecret = robotSecret;
    }

    @Override
    public @NonNull Mono<ClientResponse> filter(@NonNull ClientRequest request, ExchangeFunction next) {
        return next.exchange(request).flatMap(response -> {
            if (response.statusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                return response.releaseBody()
                        .then(acquireNewToken(robotId, robotSecret))
                        .flatMap(token -> {
                            var newRequest = ClientRequest.from(request)
                                    .headers(headers -> headers.setBearerAuth(token))
                                    .build();
                            log.warn("retrying request to {} after receiving status code 401 (unauthorized)",
                                    request.url());
                            return next.exchange(newRequest);
                        });
            } else {
                return Mono.just(response);
            }
        });
    }

    private Mono<String> acquireNewToken(String robotId, String robotSecret) {
        return authClient.requestAccessToken(robotId, robotSecret);
    }
}
