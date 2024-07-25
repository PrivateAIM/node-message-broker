import {Context, Effect, Layer, Runtime} from "effect";
import express, {IRouter, Request, Response} from "express";
import {MongoDbSubscriptionClient, MongoDbSubscriptionClientLive} from "./subscription-db";
import {v4} from "uuid";
import {Schema} from "@effect/schema";

/**
 * Describes an express based router for subscription related endpoints.
 */
export class SubscriptionRouter extends Context.Tag("@app/SubscriptionRouter")<
    SubscriptionRouter,
    IRouter
>() {
}

/**
 * Indicates that there was an error while trying to save a subscription.
 */
class SubscriptionSaveError {
    readonly _tag = "SubscriptionSaveError";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Indicates that a subscription could not be looked up (problem with a downstream service/system).
 */
class SubscriptionLookupError {
    readonly _tag = "SubscriptionLookupError";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Indicates that a subscription could not be found.
 */
class SubscriptionNotFoundError {
    readonly _tag = "SubscriptionNotFoundError";

    constructor(readonly message: string) {
    }
}

/**
 * Indicates that a subscription request is malformed and cannot be processed.
 */
class MalformedSubscriptionRequest {
    readonly _tag = "MalformedSubscriptionRequest";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Describes a single subscription related to a specific analysis.
 */
type Subscription = {
    id: string,
    analysisId: string,
    webhookUrl: URL
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
 * Describes the identifier of a subscription.
 */
type SubscriptionIdentifier = string & {
    _tag: "SubscriptionIdentifier"
}

/**
 * Describes a service for subscription purposes.
 */
class SubscriptionService extends Context.Tag("@app/SubscriptionService")<
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
const MongoDbBasedSubscriptionServiceLive = Layer.effect(
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

            console.log("read database");
            console.log(subscription);

            if (subscription === undefined) {
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

/**
 * Controller for routing subscription related requests.
 */
const SubscriptionControllerLive: Layer.Layer<
    SubscriptionRouter,
    never,
    SubscriptionRouter
> = Layer.effect(
    SubscriptionRouter,
    Effect.gen(function* () {
        let router = yield* SubscriptionRouter;
        const subscriptionSvc = yield* SubscriptionService;
        const runFork = Runtime.runFork(yield* Effect.runtime<never>())

        router.post("/analyses/:analysisId/messages/subscriptions", (req: Request, res: Response) => {
            const {analysisId} = req.params;
            const webhookUrl = req.body.webhookUrl;

            runFork(
                subscriptionSvc.addSubscription({analysisId: analysisId, webhookUrl: webhookUrl})
                    .pipe(
                        Effect.map((identifier: SubscriptionIdentifier) => {
                                const subscriptionResourceLocation = `${req.protocol}://${req.get('Host')}${req.originalUrl}/${identifier}`;
                                res.header('Location', subscriptionResourceLocation);
                                res.status(201).send(JSON.stringify({
                                    subscriptionId: identifier,
                                }));
                            }
                        ),
                        Effect.catchTags({
                            SubscriptionSaveError: (e: SubscriptionSaveError) => Effect.succeed(
                                res.status(502).send(
                                    JSON.stringify(e)
                                )
                            ),
                            MalformedSubscriptionRequest: (e: MalformedSubscriptionRequest) => Effect.succeed(
                                res.status(400).send(
                                    JSON.stringify(e)
                                )
                            )
                        })
                    )
            )
        });

        router.get("/analyses/:analysisId/messages/subscriptions/:subscriptionId", (req: Request, res: Response) => {
            const {analysisId, subscriptionId} = req.params;

            runFork(
                subscriptionSvc.findSubscriptionById(subscriptionId as SubscriptionIdentifier)
                    .pipe(
                        Effect.filterOrFail(
                            (subscription) => subscription.analysisId === analysisId,
                            () => new SubscriptionNotFoundError("subscription with ID: " + subscriptionId + " does not exist for analysis with ID: " + analysisId)
                        ),
                        Effect.map((subscription) =>
                            res.status(200).send(
                                JSON.stringify(subscription)
                            )
                        ),
                        Effect.catchTags({
                            SubscriptionLookupError: (e: SubscriptionLookupError) => Effect.succeed(
                                res.status(502).send(
                                    JSON.stringify(e)
                                )
                            ),
                            SubscriptionNotFoundError: (e: SubscriptionNotFoundError) => Effect.succeed(
                                res.status(404).send(
                                    JSON.stringify(e)
                                )
                            )
                        }),
                        Effect.catchAll((e: Error) => Effect.succeed(
                            res.status(500).send(
                                JSON.stringify(e)
                            )
                        ))
                    )
            )
        });

        return router;
    })
).pipe(
    Layer.provide(MongoDbBasedSubscriptionServiceLive)
);

/**
 * Express router for subscription related endpoints.
 */
export const SubscriptionRouterLive = SubscriptionControllerLive.pipe(
    Layer.provide(Layer.sync(SubscriptionRouter, () => express.Router()))
);

