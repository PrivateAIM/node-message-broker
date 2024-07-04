import {Context, Effect, Layer} from "effect";
import express, {IRouter} from "express";
import {HealthRouter, HealthRouterLive} from "./health/health";
import {RequestLoggerMiddleware, RequestLoggerMiddlewareLive} from "./middleware/request-logger";

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

        mainRouter.use(requestLogger);
        mainRouter.use(healthRouter);

        return mainRouter;
    })
).pipe(
    Layer.provide(Layer.merge(
        HealthRouterLive,
        RequestLoggerMiddlewareLive
    ))
);
