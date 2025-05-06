# Node Message Broker

![develop](https://github.com/PrivateAIM/node-message-broker/actions/workflows/ci.yml/badge.svg?branch=develop)

The communication component for asynchronously sending and receiving messages to/from other nodes. Due to the
distribution of nodes these messages are routed via the [Hub](https://github.com/PrivateAIM/hub).

## Development Setup

To run the message broker component the following prerequisites need to be met:

- running authentication provider which is OpenID compliant (e.g. Keycloak)
- running MonogDB instance (used for subscriptions)
- running Hub instance with auth, core and messenger component

To spin up a development environment run the following command:

```shell
docker compose -f dev/docker-compose.yml up -d
```

__NOTE__: _Currently, the Hub instance is not taken care of by the command above. However, this will change in future
releases. For the time being use your own instance or a public one._

## Running the Application

This application requires [Maven](https://maven.apache.org) and at least Java 21 to run. Use the following environment
variables to adjust the application to your needs:

| EnvVar                                 | Description                                                                                    | Default         |
|----------------------------------------|------------------------------------------------------------------------------------------------|-----------------|
| AUTH_JWKS_URL                          | URL to retrieve a JWKS for verifying JWTs.                                                     |                 |
| HUB_AUTH_BASE_URL                      | Base URL to reach the Hub's core component.                                                    |                 |
| HUB_AUTH_ROBOT_ID                      | Robot ID associated with the node.                                                             |                 |
| HUB_AUTH_ROBOT_SECRET_FILE             | Path to the file containing the secret of the node's associated robot account, Base64 encoded. |                 |
| HUB_BASE_URL                           | Base URL to reach the Hub's auth component.                                                    |                 |
| HUB_MESSENGER_BASE_URL                 | Base URL to reach the Hub's messenger component.                                               |                 |
| LOG_LEVEL                              | Log level being used. Can be either of `trace`, `debug`, `info`, `warn` or `error`.            | `info`          |
| MANAGEMENT_SERVER_PORT                 | Port being used by the management server (providing health check endpoints etc.)               | `8090`          |
| PERSISTENCE_DATABASE_NAME              | Database name to use when connecting to a MongoDB instance.                                    | `messagebroker` |
| PERSISTENCE_HOSTNAME                   | Hostname to use to connect to a MongoDB instance.                                              | `localhost`     |
| PERSISTENCE_PORT                       | Port to use to connect to a MongoDB instance.                                                  | `17017`         |
| SECURITY_ADDITIONAL_TRUSTED_CERTS_FILE | Path to a certificate bundle containing additional certificates to be loaded during startup.   |                 |
| SECURITY_NODE_PRIVATE_ECDH_KEY_FILE    | Path to the file containing the node's private EC key in PEM format, Base64 encoded.           |                 |
| SERVER_PORT                            | Port being used by the Web server.                                                             | `8080`          |

## Endpoint Documentation

OpenAPI compliant endpoint documentation can be accessed
at [http://localhost:<MANAGEMENT_SERVER_PORT>/actuator/swagger-ui](http://localhost:<MANAGEMENT_SERVER_PORT>/actuator/swagger-ui).
