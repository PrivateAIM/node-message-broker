import {Context, Effect, Layer, Option, Redacted} from "effect";
import {BrokerConfig, MessageBrokerConfig} from "../config";
import mongoose, {Mongoose, Schema} from "mongoose";


const SubscriptionSchema = new Schema({
    id: {type: mongoose.Schema.Types.String, required: true, immutable: true, index: {name: 'unique_id', unique: true}},
    analysisId: {type: mongoose.Schema.Types.String, required: true, immutable: true, index: 1},
    webhookUrl: {type: mongoose.Schema.Types.String, required: true, immutable: true}
});

export class MongoDbSubscriptionClient extends Context.Tag("@app/subscription/SubscriptionDbClient")<
    MongoDbSubscriptionClient,
    Mongoose
>() {
}

export const MongoDbSubscriptionClientLive: Layer.Layer<
    MongoDbSubscriptionClient,
    never,
    never
> = Layer.scoped(
    MongoDbSubscriptionClient,
    Effect.scoped(
        Effect.gen(function* () {
            const conf: MessageBrokerConfig = yield* BrokerConfig;

            let connectionStringBuilder = ["mongodb://"];

            const potentialCredentials = Option.all({
                username: conf.persistence.username,
                password: conf.persistence.password
            });
            Option.tap(potentialCredentials, (creds) => {
                if (creds.username.length > 0 && Redacted.value(creds.password).length > 0) {
                    connectionStringBuilder.push(creds.username + ":" + Redacted.value(creds.password) + "@");
                }
                return Option.none();
            });

            connectionStringBuilder.push(conf.persistence.hostname + ":" + conf.persistence.port);
            connectionStringBuilder.push("/" + conf.persistence.databaseName);
            const connectionString = connectionStringBuilder.join("");

            yield* Effect.logInfo("trying to connect to subscription database");

            return yield* Effect.acquireRelease(
                // TODO: solve this using a retry...
                Effect.tryPromise({
                    try: () => mongoose.connect(connectionString),
                    catch: (e) => e
                }).pipe(
                    Effect.map((m: Mongoose) => {
                        Effect.runSync(Effect.logInfo("registering subscription model on database client"));
                        m.model('subscription', SubscriptionSchema);
                        return m;
                    }),
                    Effect.catchAll((defect) => {
                        Effect.runSync(Effect.logError(defect));
                        return Effect.dieMessage("cannot connect to database")
                    })
                ),
                (_db: Mongoose) => {
                    return Effect.sync(() => {});
                    // TODO: implement this later on
                    // return Effect.sync(() => db.disconnect())
                }
            );
        })
    )
)
