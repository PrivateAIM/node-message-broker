package de.privateaim.node_message_broker.common.hub;

import de.privateaim.node_message_broker.common.hub.api.AnalysisNode;
import de.privateaim.node_message_broker.common.hub.api.HubResponseContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A client for communicating with the core hub services via HTTP/HTTPS.
 */
@Slf4j
public final class HttpHubClient implements HubClient {

    private final WebClient authenticatedWebClient;
    private final HttpHubClientConfig config;

    public HttpHubClient(WebClient authenticatedWebClient, HttpHubClientConfig config) {
        this.authenticatedWebClient = requireNonNull(authenticatedWebClient, "authenticated web client must not be null");
        this.config = requireNonNull(config, "config must not be null");
    }

    // TODO: this might use a cache to cut corners and improve performance by avoiding unnecessary round-trips
    @Override
    public Mono<List<AnalysisNode>> fetchAnalysisNodes(String analysisId) {
        return authenticatedWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/analysis-nodes")
                        .queryParam("filter[analysis_id]", analysisId)
                        .queryParam("include", "node")
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError,
                        response -> {
                            var err = new HubCoreServerException("could not fetch analysis nodes for analysis `%s` from hub"
                                    .formatted(analysisId));

                            log.warn("retrying to request analysis nodes for analysis `{}` from hub after failed attempt",
                                    analysisId, err);
                            return Mono.error(err);
                        })
                .bodyToMono(new ParameterizedTypeReference<HubResponseContainer<List<AnalysisNode>>>() {
                })
                .map(resp -> resp.data)
                .retryWhen(Retry.backoff(config.maxRetries(), Duration.ofMillis(config.retryDelayMs()))
                        .jitter(0.75)
                        .filter(err -> err instanceof HubCoreServerException)
                        .onRetryExhaustedThrow(((retryBackoffSpec, retrySignal) ->
                                new HubAnalysisNodesNotObtainable("exhausted maximum number of retries of '%d'"
                                        .formatted(config.maxRetries())))));
    }
}
