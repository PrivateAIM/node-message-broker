package de.privateaim.node_message_broker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, prePostEnabled = false)
public class WebSecurityConfig {

    @Value("${app.auth.jwksUrl}")
    private String jwksUrl;

    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        // TODO: do not keep this open in production -> revise after overhaul
        return http.authorizeHttpRequests(req ->
                        req.anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwkSetUri(jwksUrl)
                        )
                )
                .build();
    }
}
