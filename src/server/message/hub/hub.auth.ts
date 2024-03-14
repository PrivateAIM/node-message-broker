import type { APIClient as AuthupAPIClient } from '@authup/core';

/**
 * An authentication service for the central side (hub).
 */
export class HubAuth {
    private readonly apiClient: AuthupAPIClient;
    // Todo: add refresh token later on

    constructor(apiClient: AuthupAPIClient) {
        this.apiClient = apiClient;
    }

    /**
     * Obtains an auth token from the central side (hub).
     *
     * @param robotId identifier of the robot account
     * @param robotSecret secret of the robot account
     * @returns A promise containing the access token.
     */
    async obtainAuthToken(robotId: string, robotSecret: string): Promise<string> {
        return this.apiClient.token.createWithRobotCredentials({
            id: robotId,
            secret: robotSecret,
        })
            .then((res) => res.access_token);
    }
}
