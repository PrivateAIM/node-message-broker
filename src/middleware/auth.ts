import {Context, Effect, Layer, Runtime} from "effect";
import {NextFunction, Request, RequestHandler, Response} from "express";
import * as jose from 'jose';
import {BrokerConfig, MessageBrokerConfig} from "../config";

/**
 * Describes a middleware for auth checks.
 */
export class AuthMiddleware extends Context.Tag("@app/middleware/AuthMiddleware")<
    AuthMiddleware,
    RequestHandler
>() {
}

/**
 * Extracts the authentication token from the headers of the given request. Expects the request to use
 * a bearer token authentication type.
 *
 * @param {Request} request Tries to extract the authentication token from this request's headers.
 *
 * @returns {string | undefined} The extracted token or undefined.
 */
function extractBearerToken(request: Request): string | undefined {
    const [type, token] = request.headers.authorization?.split(' ') ?? [];
    return type === 'Bearer' ? token : undefined;
}

/**
 * Indicates that the token verification failed.
 */
class TokenVerificationFailedError {
    readonly _tag = "TokenVerificationFailedError";

    constructor(readonly message: string, readonly cause: Error) {
    }
}

/**
 * Main middleware for authentication purposes.
 */
export const AuthMiddlewareLive: Layer.Layer<
    AuthMiddleware,
    never,
    never
> = Layer.effect(
    AuthMiddleware,
    Effect.gen(function* () {
        const conf: MessageBrokerConfig = yield* BrokerConfig;
        const runFork = Runtime.runFork(yield* Effect.runtime<never>())

        yield* Effect.logInfo(`setting up auth middleware with JWKS URL '${conf.auth.jwksUrl}'`);
        const jwkSet = jose.createRemoteJWKSet(new URL(conf.auth.jwksUrl));

        return (request: Request, response: Response, next: NextFunction) => {
            const bearerToken = extractBearerToken(request);
            if (bearerToken === undefined) {
                response.status(401).send();
                return;
            }

            runFork(Effect.gen(function* () {
                yield* Effect.tryPromise({
                    try: () => jose.jwtVerify(bearerToken, jwkSet),
                    catch: (err) => new TokenVerificationFailedError("could not verify token", err as Error)
                });

                next();
            }).pipe(
                Effect.catchTags({
                    TokenVerificationFailedError: (e: TokenVerificationFailedError) => Effect.succeed(
                        response.status(401).send(
                            JSON.stringify(e)
                        )
                    )
                }),
                Effect.catchAll((e: Error) => Effect.succeed(
                    response.status(500).send(
                        JSON.stringify(e)
                    )
                ))
            ));
        }
    })
)
