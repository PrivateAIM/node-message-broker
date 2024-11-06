package de.privateaim.node_message_broker.common.hub.auth;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static java.util.Objects.requireNonNull;

public final class HttpHubAuthClient implements HubAuthClient {

    private final WebClient webClient;

    private static final String TOKEN_PATH = "/token";

    private HttpHubAuthClient(WebClient webClient) {
        this.webClient = requireNonNull(webClient, "web client must not be null");

    }

    public static HubAuthClient create(String baseUrl) {
        var hubAuthWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(httpHeaders -> httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .build();

        return new HttpHubAuthClient(hubAuthWebClient);
    }

    @Override
    public Mono<String> requestAccessToken(String clientId, String clientSecret) {
        return webClient.post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData("grant_type", "robot_credentials")
                        .with("id", clientId)
                        .with("secret", clientSecret))
                .exchangeToMono(clientResponse -> clientResponse.bodyToMono(HubAuthTokenResponse.class))
                .handle((tokenResponse, sink) -> {
                    // TODO: add error handling here!
                    sink.next(tokenResponse.accessToken);
                });
    }
}
