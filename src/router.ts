import {Context, Effect, Layer} from "effect";
import express, {IRouter} from "express";
import {HealthRouter, HealthRouterLive} from "./health/health";
import {RequestLoggerMiddleware, RequestLoggerMiddlewareLive} from "./middleware/request-logger";
import {DiscoveryRouter, DiscoveryRouterLive} from "./discovery/discovery";
import {SubscriptionRouter, SubscriptionRouterLive} from "./message/subscription";

/**
 * Describes the main router of this application.
 */
export class ServerRouter extends Context.Tag("ServerRouter")<
    ServerRouter,
    IRouter
>() {
}

/**
 * Main Router of this application.
 */
export const ServerRouterLive: Layer.Layer<
    ServerRouter,
    never,
    never
> = Layer.effect(
    ServerRouter,
    Effect.gen(function* () {
        let mainRouter = express.Router();
        let requestLogger = yield* RequestLoggerMiddleware;
        let healthRouter = yield* HealthRouter;
        let discoveryRouter = yield* DiscoveryRouter;
        let subscriptionRouter = yield* SubscriptionRouter;

        mainRouter.use(express.json());
        mainRouter.use(requestLogger);
        mainRouter.use(healthRouter);
        mainRouter.use(discoveryRouter);
        mainRouter.use(subscriptionRouter);

        return mainRouter;
    })
).pipe(
    Layer.provide(Layer.mergeAll(
        HealthRouterLive,
        DiscoveryRouterLive,
        SubscriptionRouterLive,
        RequestLoggerMiddlewareLive
    ))
);
