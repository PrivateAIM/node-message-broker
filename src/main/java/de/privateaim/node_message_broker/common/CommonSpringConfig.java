package de.privateaim.node_message_broker.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.privateaim.node_message_broker.ConfigurationUtil;
import de.privateaim.node_message_broker.common.hub.HttpHubClient;
import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.common.hub.auth.HubOIDCAuthenticator;
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

import java.io.IOException;
import java.util.List;

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

    @Qualifier("HUB_CORE_WEB_CLIENT")
    @Bean
    public WebClient alwaysReAuthenticatedWebClient(
            @Qualifier("HUB_AUTHENTICATION_MIDDLEWARE") ExchangeFilterFunction authenticationMiddleware,
            @Qualifier("BASE_SSL_HTTP_CLIENT_CONNECTOR") ReactorClientHttpConnector baseSslHttpClientConnector) {
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
                .clientConnector(baseSslHttpClientConnector)
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
            @Qualifier("BASE_SSL_HTTP_CLIENT_CONNECTOR") ReactorClientHttpConnector baseSslHttpClientConnector) {
        return WebClient.builder()
                .baseUrl(hubAuthBaseUrl)
                .defaultHeaders(httpHeaders -> httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .clientConnector(baseSslHttpClientConnector)
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
