package de.privateaim.node_message_broker.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.privateaim.node_message_broker.ConfigurationUtil;
import de.privateaim.node_message_broker.common.hub.HttpHubClient;
import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.common.hub.auth.HubOIDCAuthenticator;
import io.netty.handler.ssl.SslContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Configuration
public class CommonSpringConfig {

    private static final int EXCHANGE__MAX_RETRIES = 5;
    private static final int EXCHANGE__MAX_RETRY_DELAY_MS = 1000;

    @Value("${app.hub.baseUrl}")
    private String hubCoreBaseUrl;

    @Value("${app.hub.auth.baseUrl}")
    private String hubAuthBaseUrl;

    @Value("${app.hub.auth.robotId}")
    private String hubAuthRobotId;

    @Value("${app.hub.auth.robotSecretFile}")
    private String hubAuthRobotSecretFile;

    @Value("${app.proxy.host}")
    private String proxyHost;

    @Value("${app.proxy.port}")
    private Integer proxyPort;

    @Value("${app.proxy.whitelist}")
    private String proxyWhitelist;

    @Value("${app.proxy.username}")
    private String proxyUsername;

    @Value("${app.proxy.passwordFile}")
    private String proxyPasswordFile;

    @Qualifier("HUB_AUTH_ROBOT_SECRET")
    @Bean
    public String hubAuthRobotSecret() throws IOException {
        return new String(ConfigurationUtil.readExternalFileContent(hubAuthRobotSecretFile));
    }

    @Qualifier("HUB_AUTH_ROBOT_ID")
    @Bean
    public String hubAuthRobotId() {
        return hubAuthRobotId;
    }

    @Qualifier("HUB_EXCHANGE_RETRY_CONFIG")
    @Bean
    HttpRetryConfig exchangeRetryConfig() {
        return new HttpRetryConfig(EXCHANGE__MAX_RETRIES, EXCHANGE__MAX_RETRY_DELAY_MS);
    }

    @Qualifier("CORE_HTTP_CLIENT")
    @Bean
    HttpClient decoratedHttpClient(@Qualifier("COMMON_NETTY_SSL_CONTEXT") SslContext sslContext) {
        var client = HttpClient.create();
        decorateClientWithSSLContext(client, sslContext);
        decorateClientWithProxySettings(client);

        return client;
    }

    private void decorateClientWithSSLContext(HttpClient client, SslContext sslContext) {
        client.secure(t -> t.sslContext(sslContext));
    }

    private void decorateClientWithProxySettings(HttpClient client) {
        if (!proxyHost.isBlank() && proxyPort != null) {
            log.info("configuring usage of proxy at `{}:{}` with the following hosts whitelisted (via regex): `{}`",
                    proxyHost, proxyPort, proxyWhitelist);
            client.proxy(proxy -> {
                        var proxyBuilder = proxy.type(ProxyProvider.Proxy.HTTP)
                                .host(proxyHost)
                                .port(proxyPort);

                        if (!proxyWhitelist.isBlank()) {
                            log.info("configuring whitelist for proxy at `{}:{}`", proxyHost, proxyPort);
                            proxyBuilder.nonProxyHosts(proxyWhitelist);
                        } else {
                            log.info("skipping whitelist configuration for proxy at `{}:{}` since no whitelist " +
                                    "is configured", proxyHost, proxyPort);
                        }

                        if (!proxyUsername.isBlank() && !proxyPasswordFile.isBlank()) {
                            try {
                                log.info("configuring authentication for proxy");
                                var proxyPassword = Files.readString(Paths.get(proxyPasswordFile));
                                proxyBuilder.username(proxyUsername)
                                        .password((_username) -> proxyPassword);
                            } catch (IOException e) {
                                log.error("cannot read password file for proxy at `{}`", proxyPasswordFile, e);
                                throw new RuntimeException(e);
                            }
                        } else {
                            log.info("skipping authentication configuration for proxy at `{}:{}` since no " +
                                    "credentials are configured", proxyHost, proxyPort);
                        }
                    }
            );
        } else {
            log.info("skipping proxy configuration due to no specified settings");
        }
    }

    @Qualifier("CORE_HTTP_CONNECTOR")
    @Bean
    ReactorClientHttpConnector httpConnector(@Qualifier("CORE_HTTP_CLIENT") HttpClient httpClient) {
        return new ReactorClientHttpConnector(httpClient);
    }

    @Qualifier("HUB_CORE_WEB_CLIENT")
    @Bean
    public WebClient alwaysReAuthenticatedWebClient(
            @Qualifier("HUB_AUTHENTICATION_MIDDLEWARE") ExchangeFilterFunction authenticationMiddleware,
            @Qualifier("CORE_HTTP_CONNECTOR") ReactorClientHttpConnector httpConnector) {
        // We can't use Spring's default security mechanisms out-of-the-box here since HUB uses a non-standard grant
        // type which is not supported. There's a way by using a custom grant type accompanied by a client manager.
        // However, this endeavour is not pursued for the sake of simplicity.
        // Thus, we simply authenticate every request with a new token (no refresh, nothing). This will lead
        // to a potential unnecessary round-trip but can be enhanced later on with logic for using a valid
        // token until it's not valid anymore requiring a refresh procedure.
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(hubCoreBaseUrl);
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);

        return WebClient.builder()
                .uriBuilderFactory(factory)
                .defaultHeaders(httpHeaders -> httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .filter(authenticationMiddleware)
                .clientConnector(httpConnector)
                .build();
    }

    @Bean
    public HubClient hubClient(
            @Qualifier("HUB_CORE_WEB_CLIENT") WebClient webClient,
            @Qualifier("HUB_EXCHANGE_RETRY_CONFIG") HttpRetryConfig retryConfig
    ) {
        return new HttpHubClient(webClient, retryConfig);
    }

    @Qualifier("HUB_AUTH_WEB_CLIENT")
    @Bean
    WebClient hubAuthWebClient(
            @Qualifier("CORE_HTTP_CONNECTOR") ReactorClientHttpConnector httpConnector) {
        return WebClient.builder()
                .baseUrl(hubAuthBaseUrl)
                .defaultHeaders(httpHeaders -> httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .clientConnector(httpConnector)
                .build();
    }

    @Qualifier("HUB_JSON_MAPPER")
    @Bean
    ObjectMapper simpleJsonMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Qualifier("HUB_AUTHENTICATOR")
    @Bean
    OIDCAuthenticator hubAuthenticator(
            @Qualifier("HUB_AUTH_WEB_CLIENT") WebClient webClient,
            @Qualifier("HUB_EXCHANGE_RETRY_CONFIG") HttpRetryConfig retryConfig,
            @Qualifier("HUB_AUTH_ROBOT_ID") String hubAuthRobotId,
            @Qualifier("HUB_AUTH_ROBOT_SECRET") String hubAuthRobotSecret,
            @Qualifier("HUB_JSON_MAPPER") ObjectMapper jsonMapper
    ) {
        return HubOIDCAuthenticator.builder()
                .usingWebClient(webClient)
                .withRetryConfig(retryConfig)
                .withAuthCredentials(hubAuthRobotId, hubAuthRobotSecret)
                .withJsonDecoder(jsonMapper)
                .build();
    }

    @Qualifier("HUB_AUTHENTICATION_MIDDLEWARE")
    @Bean
    ExchangeFilterFunction hubAuthenticationMiddleware(
            @Qualifier("HUB_AUTHENTICATOR") OIDCAuthenticator authenticator
    ) {
        return new OIDCAuthenticatorMiddleware(authenticator);
    }

    @Primary
    @Bean
    public TaskExecutor defaultTaskExecutor() {
        return new VirtualThreadTaskExecutor();
    }
}
