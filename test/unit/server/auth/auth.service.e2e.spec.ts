import type { TestingModule } from '@nestjs/testing';
import { Test } from '@nestjs/testing';
import { TestAuthEnvironment } from '../../../util/auth';
import type { JWKS } from '../../../../src/server/auth/auth.service';
import { AuthService } from '../../../../src/server/auth/auth.service';

describe('Auth Service', () => {
    let authEnv: TestAuthEnvironment;
    let jwks: JWKS;
    let service: AuthService;

    beforeAll(async () => {
        authEnv = await TestAuthEnvironment.start();
        jwks = authEnv.getJWKSFn();

        const module: TestingModule = await Test.createTestingModule({
            providers: [
                {
                    provide: AuthService,
                    useFactory: () => new AuthService(jwks),
                },
            ],
        })
            .compile();

        service = await module.get(AuthService);
    }, 300000); // timeout takes into account that this image might have to be pulled first

    afterAll(async () => {
        await authEnv.stop();
    });

    it('should fail to verify an invalid token', async () => {
        const isValid = await service.verifyTokenAsync('eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI5ZndiOWlYYnhpWWFMQkxWekZQemU3eXlNbnYzTkpCNlc3SmxiYWZuUzI4In0.eyJleHAiOjE3MDgwMDEzMDQsImlhdCI6MTcwODAwMTAwNCwianRpIjoiNjk4YzQ4YTktYzQ3YS00ODNkLTlkN2YtNWY2Mjk1MDkxNjlhIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDoxODA4MC9yZWFsbXMvbm9kZS1tZXNzYWdlLWJyb2tlci10ZXN0Iiwic3ViIjoiYjlmMzRmZTgtYTk3My00YTE0LTk4MzYtMjY1YTcyNTdiYTNhIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoidGVzdCIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiLyoiXSwic2NvcGUiOiJwcm9maWxlIGVtYWlsIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJjbGllbnRIb3N0IjoiMTcyLjE3LjAuMSIsInByZWZlcnJlZF91c2VybmFtZSI6InNlcnZpY2UtYWNjb3VudC10ZXN0IiwiY2xpZW50QWRkcmVzcyI6IjE3Mi4xNy4wLjEiLCJjbGllbnRfaWQiOiJ0ZXN0In0.CfnDy-34LdXG4wQRU0gBgQY-FcJZ6DH_4KzvQuJ42F_jekOg0hU7jEaylh0E1ZiQVz33nsDMoL9GpcIS138Xtcz5mKZzIEnaXCvDTnXsUsBt_2dD-8zNU2GpHjQVodg27bNKbw2XxWNqHJQDo0J4p0vvIAHtmPddykYjPOf1b9qGz7g70JeqZimDawdzx0s2p9SCeDLeITPM5pxKCdadI7EMFuLQBef-GW1SP24GiPxwWGY67sFDifPbGITxFpROU2ZwOIBKPj8vKsA2BE5aun2YuAHYSxtGcB9QYTNh3BvSCu1_LrRcOB1NxYAyGGrsrQN4G_hUVp-UvgiA1mAphQ');
        expect(isValid).toBeFalsy();
    });

    it('should succeed to verify a valid token', async () => {
        const token = await authEnv.issueJWT();
        const isValid = await service.verifyTokenAsync(token);
        expect(isValid).toBeTruthy();
    });
});
