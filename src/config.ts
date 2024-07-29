import {Config, ConfigError, Effect, Option, Redacted} from "effect"

/**
 * Defines the overall configuration schema for the message broker application.
 */
export type MessageBrokerConfig = {
    readonly hub: HubClientConfig,
    readonly persistence: MessageBrokerPersistenceConfig
}

/**
 * Defines the configuration for the Hub client.
 */
export type HubClientConfig = {
    readonly baseUrl: string,
    readonly messengerBaseUrl: string,
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
 * Defines the persistence configuration for the message broker application.
 */
export type MessageBrokerPersistenceConfig = {
    readonly username: Option.Option<string>,
    readonly password: Option.Option<Redacted.Redacted>,
    readonly hostname: string,
    readonly port: number,
    readonly databaseName: string
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
        Config.string("MESSENGER_BASE_URL").pipe(
            Config.validate({
                message: "Expected a valid URL",
                validation: (url) => URL.canParse(url)
            })
        ),
        hubClientAuthConfig
    ]),
    ([baseUrl, messengerBaseUrl, auth]) => ({
        baseUrl, messengerBaseUrl, auth
    } as HubClientConfig)
), "HUB");

/**
 * Definition of the configuration associated with the message broker's persistence layer.
 * This configuration resides under the 'PERSISTENCE' prefix.
 */
const persistenceConfig = Config.nested(Config.map(
    Config.all([
        Config.string("USERNAME").pipe(
            Config.option
        ),
        Config.redacted("PASSWORD").pipe(
            Config.option
        ),
        Config.string("HOSTNAME").pipe(
            Config.validate({
                message: "Expected a valid hostname",
                // TODO: add proper validation - there's a regex for hostnames!!!
                validation: (hostname: string) => hostname.length > 0
            })
        ),
        Config.number("PORT").pipe(
            Config.withDefault(27017),
            Config.validate({
                message: "Expected a valid port number",
                validation: (port: number) => port >= 0 && port <= 65535
            })
        ),
        Config.string("DATABASE_NAME").pipe(
            Config.validate({
                message: "Expected a valid database name",
                validation: (databaseName: string) => databaseName.length > 0
            })
        )
    ]),
    ([username, password, hostname, port, databaseName]) => ({
        username, password, hostname, port, databaseName
    } as MessageBrokerPersistenceConfig)
), "PERSISTENCE");

/**
 * Reads the {@link MessageBrokerConfig} from environment variables and returns it.
 * Dies if the configuration cannot be read.
 */
export const BrokerConfig = Effect.gen(function* () {
    return yield* Config.map(
        Config.all([
            hubClientConfig,
            persistenceConfig
        ]),
        ([hub, persistence]) => ({
            hub, persistence
        } as MessageBrokerConfig)
    )
}).pipe(
    Effect.catchAll((err: ConfigError.ConfigError) => Effect.die(err))
);


