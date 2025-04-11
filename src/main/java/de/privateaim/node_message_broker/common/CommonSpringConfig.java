package de.privateaim.node_message_broker.common;

import de.privateaim.node_message_broker.ConfigurationUtil;
import de.privateaim.node_message_broker.common.hub.HttpHubClient;
import de.privateaim.node_message_broker.common.hub.HttpHubClientConfig;
import de.privateaim.node_message_broker.common.hub.HubClient;
import de.privateaim.node_message_broker.common.hub.auth.HttpHubAuthClient;
import de.privateaim.node_message_broker.common.hub.auth.HttpHubAuthClientConfig;
import de.privateaim.node_message_broker.common.hub.auth.HubAuthClient;
import de.privateaim.node_message_broker.common.hub.auth.RenewAuthTokenFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Configuration
public class CommonSpringConfig {

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
        var robotSecretFileContent = ConfigurationUtil.readExternalFileContent(hubAuthRobotSecretFile);
        var decodedRobotSecret = Base64.getDecoder().decode(robotSecretFileContent);
        return new String(decodedRobotSecret);
    }

    @Qualifier("HUB_AUTH_ROBOT_ID")
    @Bean
    public String hubAuthRobotId() {
        return hubAuthRobotId;
    }

    @Qualifier("HUB_CORE_WEB_CLIENT")
    @Bean
    public WebClient alwaysReAuthenticatedWebClient(
            @Qualifier("HUB_AUTH_RENEW_TOKEN") ExchangeFilterFunction renewTokenFilter) {
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
                .filter(renewTokenFilter)
                .build();
    }

    @Bean
    public HubClient hubClient(@Qualifier("HUB_CORE_WEB_CLIENT") WebClient alwaysReAuthenticatedWebClient) {
        var clientConfig = new HttpHubClientConfig.Builder()
                .withMaxRetries(5)
                .withRetryDelayMs(1000)
                .build();

        return new HttpHubClient(alwaysReAuthenticatedWebClient, clientConfig);
    }

    @Qualifier("HUB_AUTH_WEB_CLIENT")
    @Bean
    WebClient hubAuthWebClient() {
        return WebClient.builder()
                .baseUrl(hubAuthBaseUrl)
                .defaultHeaders(httpHeaders -> httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .build();
    }

    @Qualifier("HUB_AUTH_CLIENT")
    @Bean
    public HubAuthClient hubAuthClient(@Qualifier("HUB_AUTH_WEB_CLIENT") WebClient webClient) {
        var clientConfig = new HttpHubAuthClientConfig.Builder()
                .withMaxRetries(5)
                .withRetryDelayMs(1000)
                .build();
        return new HttpHubAuthClient(webClient, clientConfig);
    }

    @Qualifier("HUB_AUTH_RENEW_TOKEN")
    @Bean
    ExchangeFilterFunction renewAuthTokenFilter(
            @Qualifier("HUB_AUTH_CLIENT") HubAuthClient hubAuthClient,
            @Qualifier("HUB_AUTH_ROBOT_SECRET") String hubAuthRobotSecret) {
        return new RenewAuthTokenFilter(hubAuthClient, hubAuthRobotId, hubAuthRobotSecret);
    }
}
