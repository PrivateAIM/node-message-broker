package de.privateaim.node_message_broker.discovery;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscoverySpringConfig {

    @Value("${app.hub.auth.robotId}")
    private String selfRobotId;

    @Qualifier("DISCOVERY_SELF_ROBOT_ID")
    @Bean
    public String selfRobotId() {
        return selfRobotId;
    }
}
