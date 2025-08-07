package de.privateaim.node_message_broker.common.hub;

import de.privateaim.node_message_broker.common.HttpRetryConfig;
import de.privateaim.node_message_broker.common.hub.api.AnalysisNode;
import de.privateaim.node_message_broker.common.hub.api.HubResponseContainer;
import de.privateaim.node_message_broker.common.hub.api.Node;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A client for communicating with the core hub services via HTTP/HTTPS.
 */
@Slf4j
public final class HttpHubClient implements HubClient {

    private final WebClient authenticatedWebClient;
    private final HttpRetryConfig retryConfig;

    public HttpHubClient(WebClient authenticatedWebClient, HttpRetryConfig retryConfig) {
        this.authenticatedWebClient = requireNonNull(authenticatedWebClient, "authenticated web client must not be null");
        this.retryConfig = requireNonNull(retryConfig, "retry config must not be null");
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
                .retryWhen(Retry.backoff(retryConfig.maxRetries(), Duration.ofMillis(retryConfig.retryDelayMs()))
                        .jitter(0.75)
                        .filter(err -> err instanceof HubCoreServerException)
                        .onRetryExhaustedThrow(((retryBackoffSpec, retrySignal) ->
                                new HubAnalysisNodesNotObtainable("exhausted maximum number of retries of '%d'"
                                        .formatted(retryConfig.maxRetries())))));
    }

    // TODO: add cache here! - see spring annotations
    @Override
    public Mono<ECPublicKey> fetchPublicKey(String robotId) {
        return authenticatedWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/nodes")
                        .queryParam("filter[robot_id]", robotId)
                        .build()
                )
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError,
                        response -> {
                            var err = new HubCoreServerException(("could not fetch public key for node with robot id" +
                                    " `%s` from hub").formatted(robotId));

                            log.warn("retrying to request public key for node with robot id `{}` from hub after" +
                                    " failed attempt", robotId, err);
                            return Mono.error(err);
                        })
                .bodyToMono(new ParameterizedTypeReference<HubResponseContainer<List<Node>>>() {
                })
                .flatMap(resp -> {
                    if (resp.data.size() != 1) {
                        return Mono.error(new NoMatchingNodeFoundException("cannot find node with robot id `%s`"
                                .formatted(robotId)));
                    }
                    if (resp.data.getFirst().publicKey == null) {
                        return Mono.error(new NoPublicKeyException("node with robot id `%s` has no public key set"
                                .formatted(robotId)));
                    }
                    return Mono.just(Hex.decode(resp.data.getFirst().publicKey.getBytes()));
                })
                .flatMap(pubKey -> {
                    try {
                        var key = (SubjectPublicKeyInfo) new PEMParser(
                                new InputStreamReader(new ByteArrayInputStream(pubKey)))
                                .readObject();
                        return Mono.just((ECPublicKey) new JcaPEMKeyConverter().getPublicKey(key));
                    } catch (IOException e) {
                        return Mono.error(new MalformedPublicKeyException("failed to read public key from node with " +
                                "robot id `%s`".formatted(robotId), e));
                    }
                })
                .retryWhen(Retry.backoff(retryConfig.maxRetries(), Duration.ofMillis(retryConfig.retryDelayMs()))
                        .jitter(0.75)
                        .filter(err -> err instanceof HubCoreServerException)
                        .onRetryExhaustedThrow(((retryBackoffSpec, retrySignal) ->
                                new HubNodePublicKeyNotObtainable("exhausted maximum number of retries of '%d'"
                                        .formatted(retryConfig.maxRetries())))));
    }
}
