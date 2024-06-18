import {Context, Effect, Layer, Runtime} from "effect";
import {Schema} from "@effect/schema";
import {Response} from "express";
import {Express} from "../server";

/**
 * Defines 2 states of operation (healthy, unhealthy).
 */
enum OperationalStatus {
    Healthy = "healthy",
    Unhealthy = "unhealthy"
}

/**
 * A schema that describes the health status of a subsystem.
 */
class SubsystemHealthStatus extends Schema.Class<SubsystemHealthStatus>("SubsystemHealthStatus")({
    system: Schema.String.pipe(Schema.nonEmpty()),
    status: Schema.Enums(OperationalStatus)
}) {
}

/**
 * A schema that describes the overall health status of this application.
 */
class HealthStatus extends Schema.Class<HealthStatus>("HealthStatus")({
    status: Schema.Enums(OperationalStatus),
    subsystems: Schema.Array(SubsystemHealthStatus)
}) {
}

/**
 * Describes a health service.
 */
class HealthService extends Context.Tag("@app/HealthService")<
    HealthService,
    {
        /**
         * Returns an effect that cannot fail with information about the application's overall state.
         */
        readonly getAppHealthStatus: () => Effect.Effect<HealthStatus, never, never>;
    }
>() {
}

/**
 * Health service implementation that currently always reports an operational app state.
 */
const HealthServiceLive = Layer.succeed(
    HealthService,
    HealthService.of({
        getAppHealthStatus: () => Effect.succeed(
            // TODO: this needs to be adjusted later on (database connection, socket etc.)
            new HealthStatus({
                status: OperationalStatus.Healthy,
                subsystems: []
            })
        )
    })
);

/**
 * Express controller for routing health related requests.
 */
const HealthControllerLive: Layer.Layer<
    never,
    never,
    Express | HealthService
> = Layer.effectDiscard(
    Effect.gen(function* () {
        const app = yield* Express;
        const healthSvc = yield* HealthService;
        const runFork = Runtime.runFork(yield* Effect.runtime<never>())

        app.get("/health", (_: any, res: Response) => {
            runFork(healthSvc.getAppHealthStatus().pipe(
                Effect.map((status) =>
                    res.status(200).send(
                        JSON.stringify(status, null, 2)
                    )
                )
            ));
        });
    })
);

/**
 * Ready to be used router effect for health related functionality. Use this effect in conjunction with Express in order
 * to enable the /health route.
 */
export const HealthRouteLive: Layer.Layer<
    never,
    never,
    Express
> = HealthControllerLive.pipe(
    Layer.provide(HealthServiceLive)
);
