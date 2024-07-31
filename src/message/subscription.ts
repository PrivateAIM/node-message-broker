import {Context, Effect, Layer, Runtime} from "effect";
import express, {IRouter, Request, Response} from "express";
import {
    MalformedSubscriptionRequest,
    MongoDbBasedSubscriptionServiceLive,
    SubscriptionIdentifier,
    SubscriptionLookupError,
    SubscriptionNotFoundError,
    SubscriptionSaveError,
    SubscriptionService
} from "../common/node-subscription";

/**
 * Describes an express based router for subscription related endpoints.
 */
export class SubscriptionRouter extends Context.Tag("@app/SubscriptionRouter")<
    SubscriptionRouter,
    IRouter
>() {
}

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

