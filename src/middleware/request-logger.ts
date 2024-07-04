import {Context, Effect, Layer} from "effect";
import {NextFunction, Request, RequestHandler, Response} from "express";

/**
 * Describes a middleware for logging requests.
 */
export class RequestLoggerMiddleware extends Context.Tag("@app/RequestLoggerMiddleware")<
    RequestLoggerMiddleware,
    RequestHandler
>() {
}

/**
 * Main middleware for logging requests.
 */
export const RequestLoggerMiddlewareLive: Layer.Layer<
    RequestLoggerMiddleware,
    never,
    never
> = Layer.effect(
    RequestLoggerMiddleware,
    Effect.gen(function* () {
        return (request: Request, response: Response, next: NextFunction) => {
            const {ip, method, path: url} = request;

            response.on('finish', () => {
                const {statusCode} = response;
                const contentLength = response.get('content-length');

                Effect.runSync(Effect.logInfo(`${method} ${url} ${statusCode} ${contentLength} - ${ip}`));
            });
            next();
        }
    })
)
