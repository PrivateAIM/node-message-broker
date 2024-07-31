import {Context, Effect, Layer} from "effect";
import {Schema} from "@effect/schema";
import {MongoDbSubscriptionClient, MongoDbSubscriptionClientLive} from "./node-subscription-db";
import {v4} from "uuid";

/**
 * Indicates that there was an error while trying to save a subscription.
 */
export class SubscriptionSaveError {
    readonly _tag = "SubscriptionSaveError";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Indicates that a subscription could not be looked up (problem with a downstream service/system).
 */
export class SubscriptionLookupError {
    readonly _tag = "SubscriptionLookupError";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Indicates that a subscription could not be found.
 */
export class SubscriptionNotFoundError {
    readonly _tag = "SubscriptionNotFoundError";

    constructor(readonly message: string) {
    }
}

/**
 * Indicates that a subscription request is malformed and cannot be processed.
 */
export class MalformedSubscriptionRequest {
    readonly _tag = "MalformedSubscriptionRequest";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Describes the request necessary for setting up a new subscription.
 */
const SubscriptionRequest = Schema.required(
    Schema.Struct({
        analysisId: Schema.String.pipe(Schema.minLength(1)),
        webhookUrl: Schema.String.pipe(
            Schema.filter((value) => URL.canParse(value))
        )
    })
);

/**
 * Describes a single subscription related to a specific analysis.
 */
type Subscription = {
    id: string,
    analysisId: string,
    webhookUrl: URL
}

/**
 * Describes the identifier of a subscription.
 */
export type SubscriptionIdentifier = string & {
    _tag: "SubscriptionIdentifier"
}

/**
 * Describes a service for subscription purposes.
 */
export class SubscriptionService extends Context.Tag("@app/common/SubscriptionService")<
    SubscriptionService,
    {
        /**
         * Finds all subscriptions for a specific analysis.
         *
         * @param analysisId identifies the analysis
         * @returns All subscriptions associated with the analysis.
         */
        readonly findSubscriptionsByAnalysis: (analysisId: String) => Effect.Effect<
            Subscription[],
            SubscriptionLookupError,
            never
        >,

        /**
         * Finds a subscription by its ID.
         *
         * @param subscriptionIdentifier identifies the subscription
         * @return The subscription.
         */
        readonly findSubscriptionById: (subscriptionIdentifier: SubscriptionIdentifier) => Effect.Effect<
            Subscription,
            SubscriptionLookupError | SubscriptionNotFoundError,
            never
        >

        /**
         * Adds a new message subscription.
         *
         * @param subscriptionRequest information about the subscription that shall be added
         * @return The identifier of the added subscription.
         */
        readonly addSubscription: (subscriptionRequest: typeof SubscriptionRequest.Type) => Effect.Effect<
            SubscriptionIdentifier,
            SubscriptionSaveError | MalformedSubscriptionRequest,
            never
        >
    }
>() {
}

/**
 * {@link SubscriptionService} implementation that makes use of {@link MongoDbSubscriptionClient} to manage subscriptions.
 */
export const MongoDbBasedSubscriptionServiceLive = Layer.effect(
    SubscriptionService,
    Effect.gen(function* () {
        const subscriptionDbClient = yield* MongoDbSubscriptionClient;

        /**
         * Finds all subscriptions for a specific analysis.
         *
         * @param analysisId identifies the analysis
         * @returns All subscriptions associated with the analysis.
         */
        const findSubscriptionsByAnalysis = (analysisId: String) => Effect.gen(function* () {
            const subscriptions = yield* Effect.tryPromise({
                try: () => subscriptionDbClient.models.subscription.find({analysisId: analysisId}),
                catch: (err) => new SubscriptionLookupError("failed searching for subscriptions", err as Error)
            });

            return subscriptions.flatMap((subscription) => ({
                id: subscription.id,
                analysisId: subscription.analysisId,
                webhookUrl: subscription.webhookUrl
            } as Subscription))
        });

        /**
         * Finds a subscription by its ID.
         *
         * @param subscriptionIdentifier identifies the subscription
         * @return The subscription.
         */
        const findSubscriptionById = (subscriptionIdentifier: SubscriptionIdentifier) => Effect.gen(function* () {
            const subscription = yield* Effect.tryPromise({
                try: () => subscriptionDbClient.models.subscription.findOne({id: subscriptionIdentifier}),
                catch: (err) => new SubscriptionLookupError("failed searching for subscription", err as Error)
            })

            if (subscription === undefined || subscription === null) {
                yield* Effect.fail(new SubscriptionNotFoundError("could not find a subscription with ID: " + subscriptionIdentifier))
            }

            return {
                id: subscription.id,
                analysisId: subscription.analysisId,
                webhookUrl: subscription.webhookUrl
            } as Subscription;
        });

        /**
         * Adds a new message subscription.
         *
         * @param subscriptionRequest information about the subscription that shall be added
         * @return The identifier of the added subscription.
         */
        const addSubscription = (subscriptionRequest: typeof SubscriptionRequest.Type) => Effect.gen(function* () {
            const parsedSubscriptionRequest = yield* Effect.try({
                try: () => Schema.decodeUnknownSync(SubscriptionRequest, {errors: "all"})(subscriptionRequest),
                catch: (err) => new MalformedSubscriptionRequest("received malformed subscription request data", err as Error)
            });

            const subscriptionId = v4();
            yield* Effect.tryPromise({
                try: () => new subscriptionDbClient.models.subscription({
                    id: subscriptionId,
                    analysisId: parsedSubscriptionRequest.analysisId,
                    webhookUrl: parsedSubscriptionRequest.webhookUrl
                }).save(),
                catch: (err) => new SubscriptionSaveError("could not save subscription", err as Error)
            })

            return subscriptionId as SubscriptionIdentifier;
        });

        return {
            findSubscriptionsByAnalysis,
            findSubscriptionById,
            addSubscription
        }
    })
).pipe(
    Layer.provide(MongoDbSubscriptionClientLive)
)
