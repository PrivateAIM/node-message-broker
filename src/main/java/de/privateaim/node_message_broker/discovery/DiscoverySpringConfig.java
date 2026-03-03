package de.privateaim.node_message_broker.discovery;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscoverySpringConfig {

    @Value("${app.hub.auth.clientId}")
    private String selfClientId;

    @Qualifier("DISCOVERY_SELF_CLIENT_ID")
    @Bean
    public String selfClientId() {
        return selfClientId;
    }
}
