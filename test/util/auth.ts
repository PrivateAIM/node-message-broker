import * as jose from 'jose';
import { resolve } from 'path';
import type { StartedTestContainer } from 'testcontainers';
import { GenericContainer, Wait } from 'testcontainers';
import axios from 'axios';
import type { JWKS } from '../../src/server/auth/auth.service';

const REALM_CONFIG_FILE = resolve('./test/resources/keycloak-test-realm.json');
const TEST_NODE_KEYCLOAK_REALM: string = 'node-message-broker-test';
const TEST_NODE_KEYCLOAK_CLIENT_SECRET: string = 'uGyA5g91eg3ImP7xHx6ADoYn9g7C92yk';
const TEST_NODE_KEYCLOAK_CLIENT_ID: string = 'test';

/**
 * Describes the response of a JWT issue request to Keycloak when using a service account.
 */
type ObtainTokenResponse = {
    access_token: string,
    expires_in: number,
    refresh_expires_in: number,
    token_type: string,
    not_before_policy: number,
    scope: string
};

/**
 * Primitive utility for providing an authentication environment for end-to-end tests.
 * Builds on top of a live Keycloak instance running in a Docker container.
 */
export class TestAuthEnvironment {
    private readonly authContainer: StartedTestContainer;

    constructor(authContainer: StartedTestContainer) {
        this.authContainer = authContainer;
    }

    // TODO: refactor - let clients pass realms and configuration (more flexible for different use cases)
    /**
     * Creates a running authentication environment that is ready to be used.
     *
     * @returns {Promise<TestAuthEnvironment>} A promise of the authentication environment.
     */
    static async start(): Promise<TestAuthEnvironment> {
        const authContainer = await new GenericContainer('bitnami/keycloak:23.0.6')
            .withCommand(['kc.sh', 'start-dev', '--import-realm', '--health-enabled=true'])
            .withExposedPorts(8080)
            .withEnvironment({
                KEYCLOAK_DATABASE_VENDOR: 'h2',
            })
            .withBindMounts([{
                source: REALM_CONFIG_FILE,
                target: '/opt/bitnami/keycloak/data/import/realm.json',
                mode: 'ro',
            }])
            .withWaitStrategy(Wait.forHttp('/health/ready', 8080)
                .withStartupTimeout(50000)
                .withReadTimeout(10000))
            .start();
        return new TestAuthEnvironment(authContainer);
    }

    /**
     * Stops the authentication environment and cleans up underlying Docker containers.
     */
    async stop() {
        await this.authContainer.stop();
    }

    /**
     * Gets a function that can be used to retrieve JSON Web Key Sets from this authentication environment.
     *
     * @returns {JWKS} A function for retrieving JSON Web Key Sets.
     */
    getJWKSFn(): JWKS {
        const host = this.authContainer.getHost();
        const port = this.authContainer.getFirstMappedPort();
        const jwksUrl: URL = new URL(`http://${host}:${port}/realms/${TEST_NODE_KEYCLOAK_REALM}/protocol/openid-connect/certs`);

        return jose.createRemoteJWKSet(jwksUrl);
    }

    /**
     * Issues a new JWT from this authentication environment.
     *
     * @returns {Promise<string>} A promise of the issued token.
     */
    async issueJWT(): Promise<string> {
        const host = this.authContainer.getHost();
        const port = this.authContainer.getFirstMappedPort();
        const obtainTokenUrl: URL = new URL(`http://${host}:${port}/realms/${TEST_NODE_KEYCLOAK_REALM}/protocol/openid-connect/token`);

        const params = new URLSearchParams({
            grant_type: 'client_credentials',
            client_id: TEST_NODE_KEYCLOAK_CLIENT_ID,
            client_secret: TEST_NODE_KEYCLOAK_CLIENT_SECRET,
        });

        return axios.post<ObtainTokenResponse>(obtainTokenUrl.toString(), params, {
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            withCredentials: false,
        })
            .then((response) => response.data.access_token);
    }
}
