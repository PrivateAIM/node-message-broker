package de.privateaim.node_message_broker;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.util.Loggers;

import java.security.Security;

@SpringBootApplication
public class NodeMessageBrokerApp {

    public static void main(String[] args) {
        var securityProvider = new BouncyCastleProvider();
        Security.addProvider(securityProvider);

        Loggers.useSl4jLoggers(); // ensure slf4j loggers are used!
        SpringApplication.run(NodeMessageBrokerApp.class, args);
    }
}
