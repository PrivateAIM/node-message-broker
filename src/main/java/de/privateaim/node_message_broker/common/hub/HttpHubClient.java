package de.privateaim.node_message_broker.common.hub;

import de.privateaim.node_message_broker.common.hub.api.AnalysisNode;
import de.privateaim.node_message_broker.common.hub.api.HubResponseContainer;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static java.util.Objects.requireNonNull;

public final class HttpHubClient implements HubClient {

    private final WebClient authenticatedWebClient;

    public HttpHubClient(WebClient authenticatedWebClient) {
        this.authenticatedWebClient = requireNonNull(authenticatedWebClient, "authenticated web client must not be null");
    }


    @Override
    public Mono<List<AnalysisNode>> fetchAnalysisNodes(String analysisId) {
        return authenticatedWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/analysis-nodes")
                        .queryParam("filter[analysis_id]", analysisId)
                        .queryParam("include", "node")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<HubResponseContainer<List<AnalysisNode>>>() {
                })
                .map(resp -> resp.data);
    }
}
