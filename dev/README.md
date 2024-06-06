# Dev

Contains a setup useful for development purposes.

> [!CAUTION]
> This is not meant to be used in production!

## Ports
Services can be accessed using the following ports:

| Service  | Ports        |
|----------|--------------|
| Keycloak | 18080, 10433 |
| MongoDB  | 17017        |

## Important information

For accessing Keycloak's admin console make use of the following information:

|               | Value |
|---------------|-------|
| Admin Username | admin |
| Admin Password | admin |


For requesting/validating an access token make use of the following information:

|               | Value                                                                  |
|---------------|------------------------------------------------------------------------|
| Realm         | privateaim                                                             |
| Client        | message-broker                                                         |
| Grant Type    | client_credentials                                                     |
| Client Secret | thtiFoImj6rvrfTvKkiOlSigRcYLbQwf                                       |
| JWKS          | http://localhost:18080/realms/privateaim/protocol/openid-connect/certs |

_The client secret is shared here since it is just a development environment that's not being used production._

