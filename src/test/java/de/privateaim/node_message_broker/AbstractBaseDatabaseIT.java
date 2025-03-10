package de.privateaim.node_message_broker;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class AbstractBaseDatabaseIT {

    private static final String MONGO_IMAGE = "mongo:7.0.5@sha256:fcde2d71bf00b592c9cabab1d7d01defde37d69b3d788c53c3bc7431b6b15de8";

    protected static MongoDBContainer mongo = new MongoDBContainer(
            DockerImageName.parse(MONGO_IMAGE)
                    .asCompatibleSubstituteFor("mongo"))
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void containersProperties(DynamicPropertyRegistry registry) {
        mongo.start();
        registry.add("spring.data.mongodb.host", mongo::getHost);
        registry.add("spring.data.mongodb.port", mongo::getFirstMappedPort);
    }
}
