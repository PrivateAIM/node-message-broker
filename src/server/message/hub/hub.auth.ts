import { APIClient as AuthupAPIClient } from '@authup/core';

export class HubAuth {

    private readonly apiClient: AuthupAPIClient;
    // Todo: add refresh token later on

    constructor(apiClient: AuthupAPIClient) {
        this.apiClient = apiClient;
    }

    async obtainAuthToken(robotId: string, robotSecret: string): Promise<string> {
        return this.apiClient.token.createWithRobotCredentials({
            id: robotId,
            secret: robotSecret
        })
            .then(res => res.access_token);
    }
}
