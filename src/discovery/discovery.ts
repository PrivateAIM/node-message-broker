import {Context, Effect, Layer, Runtime} from "effect";
import express, {IRouter, Request, Response} from "express";
import {HubClientLive} from "../common/hub-client";
import {
    DiscoveryService, HubBasedDiscoveryServiceLive,
    HubFetchError,
    HubUnexpectedResultError,
    ParticipatingNode,
    SelfNotFoundError
} from "../common/hub-discovery";

/**
 * Describes an express based router for discovery related endpoints.
 */
export class DiscoveryRouter extends Context.Tag("@app/DiscoveryRouter")<
    DiscoveryRouter,
    IRouter
>() {
}

/**
 * Controller for routing discovery related requests.
 */
const DiscoveryControllerLive: Layer.Layer<
    DiscoveryRouter,
    never,
    DiscoveryRouter
> = Layer.effect(
    DiscoveryRouter,
    Effect.gen(function* () {
        let router = yield* DiscoveryRouter;
        let discoverySvc = yield* DiscoveryService;
        const runFork = Runtime.runFork(yield* Effect.runtime<never>())

        router.get("/analyses/:analysisId/participants", (req: Request, res: Response) => {
            let {analysisId} = req.params;

            runFork(
                discoverySvc.discoverParticipatingAnalysisNodes(analysisId)
                    .pipe(
                        Effect.map((discoveredNodes: ParticipatingNode[]) =>
                            res.status(200).send(
                                JSON.stringify(discoveredNodes)
                            )
                        ),
                        Effect.catchTags({
                            HubFetchError: (e: HubFetchError) => Effect.succeed(
                                res.status(502).send(
                                    JSON.stringify(e)
                                )
                            ),
                            HubUnexpectedResultError: (e: HubUnexpectedResultError) => Effect.succeed(
                                res.status(502).send(
                                    JSON.stringify(e)
                                )
                            )
                        })
                    ))
        });

        router.get("/analyses/:analysisId/participants/self", (req: Request, res: Response) => {
            let {analysisId} = req.params;

            runFork(
                discoverySvc.discoverSelf(analysisId)
                    .pipe(
                        Effect.map((discoveredNode: ParticipatingNode) =>
                            res.status(200).send(
                                JSON.stringify(discoveredNode)
                            )
                        ),
                        Effect.catchTags({
                            HubFetchError: (e: HubFetchError) => Effect.succeed(
                                res.status(502).send(
                                    JSON.stringify(e)
                                )
                            ),
                            HubUnexpectedResultError: (e: HubUnexpectedResultError) => Effect.succeed(
                                res.status(502).send(
                                    JSON.stringify(e)
                                )
                            ),
                            SelfNotFoundError: (e: SelfNotFoundError) => Effect.succeed(
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
    Layer.provide(Layer.merge(HubBasedDiscoveryServiceLive, HubClientLive))
);

/**
 * Express router for discovery related endpoints.
 */
export const DiscoveryRouterLive = DiscoveryControllerLive.pipe(
    Layer.provide(Layer.sync(DiscoveryRouter, () => express.Router()))
);

