package de.privateaim.node_message_broker;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.BsonDocument;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class AbstractBaseDatabaseIT {

    private static final String MONGO_IMAGE = "mongo:7.0.5@sha256:fcde2d71bf00b592c9cabab1d7d01defde37d69b3d788c53c3bc7431b6b15de8";
    private static final String MONGO_DB_NAME = "test-db";

    protected static MongoDBContainer mongo = new MongoDBContainer(
            DockerImageName.parse(MONGO_IMAGE)
                    .asCompatibleSubstituteFor("mongo"))
            .withExposedPorts(27017);

    protected static MongoClient mongoClient;

    @DynamicPropertySource
    static void containersProperties(DynamicPropertyRegistry registry) {
        mongo.start();
        registry.add("spring.data.mongodb.host", mongo::getHost);
        registry.add("spring.data.mongodb.port", mongo::getFirstMappedPort);
        registry.add("spring.data.mongodb.database", () -> MONGO_DB_NAME);

        var connectionString = "mongodb://%s:%s/"
                .formatted(mongo.getHost(), mongo.getFirstMappedPort());
        mongoClient = MongoClients.create(connectionString);
    }

    /**
     * Wipes the whole database without deleting any collections. Only entries within the collections will get deleted.
     * <p>
     * This functionality is necessary even though Spring uses a rollback mechanism during tests because MongoDB does
     * not have the common concept of transactions.
     */
    protected void wipeDatabase() {
        var db = mongoClient.getDatabase(MONGO_DB_NAME);

        db.listCollectionNames()
                .forEach(collName -> db.getCollection(collName)
                        .deleteMany(new BsonDocument()));
    }
}
