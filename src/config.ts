import {Config, ConfigError, Effect, Redacted} from "effect"

/**
 * Defines the overall configuration schema for the message broker application.
 */
export type MessageBrokerConfig = {
    readonly hub: HubClientConfig
}

/**
 * Defines the configuration for the Hub client.
 */
export type HubClientConfig = {
    readonly baseUrl: string,
    readonly auth: HubClientAuthConfig
}

/**
 * Defines the authentication configuration for the Hub client.
 */
export type HubClientAuthConfig = {
    readonly baseUrl: string,
    readonly robotId: string,
    readonly robotSecret: Redacted.Redacted
}

/**
 * Definition of the authentication configuration associated with the Hub client.
 * This configuration part resides under the `AUTH` prefix.
 */
const hubClientAuthConfig = Config.nested(Config.map(
    Config.all([
        Config.string("BASE_URL").pipe(
            Config.validate({
                message: "Expected a valid URL",
                validation: (url) => URL.canParse(url)
            })
        ),
        Config.string("ROBOT_ID").pipe(
            Config.validate({
                message: "Expected a non-empty robot ID",
                validation: (robotId) => robotId.length > 0
            })
        ),
        Config.redacted("ROBOT_SECRET").pipe(
            Config.validate({
                message: "Expected a non-empty robot secret",
                validation: (robotSecret) => Redacted.value(robotSecret).length > 0
            })
        )
    ]),
    ([baseUrl, robotId, robotSecret]) => ({
        baseUrl, robotId, robotSecret
    } as HubClientAuthConfig)
), "AUTH");

/**
 * Definition of the configuration associated with the Hub client.
 * This configuration resides under the `HUB` prefix.
 */
const hubClientConfig = Config.nested(Config.map(
    Config.all([
        Config.string("BASE_URL").pipe(
            Config.validate({
                message: "Expected a valid URL",
                validation: (url) => URL.canParse(url)
            })
        ),
        hubClientAuthConfig
    ]),
    ([baseUrl, auth]) => ({
        baseUrl, auth
    } as HubClientConfig)
), "HUB");

/**
 * Reads the {@link MessageBrokerConfig} from environment variables and returns it.
 * Dies if the configuration cannot be read.
 */
export const BrokerConfig = Effect.gen(function* () {
    return yield* Config.map(
        Config.all([
            hubClientConfig
        ]),
        ([hub]) => ({
            hub
        } as MessageBrokerConfig)
    )
}).pipe(
    Effect.catchAll((err: ConfigError.ConfigError) => Effect.die(err))
);


