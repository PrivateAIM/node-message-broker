import {Context, Effect, Layer} from "effect";
import {Client} from "@authup/core-http-kit";
import {BrokerConfig, MessageBrokerConfig} from "../config";

/**
 * Describes a client for communicating with the authentication system of the Hub side.
 */
export class HubAuthClient extends Context.Tag("@app/common/HubAuthClient")<
    HubAuthClient,
    Client
>() {
}

/**
 * Client for communicating with the authentication system of the Hub side.
 */
export const HubAuthClientLive: Layer.Layer<
    HubAuthClient,
    never,
    never
> = Layer.effect(
    HubAuthClient,
    Effect.gen(function* () {
        const conf: MessageBrokerConfig = yield* BrokerConfig;

        return new Client({
            baseURL: conf.hub.auth.baseUrl
        });
    })
)

