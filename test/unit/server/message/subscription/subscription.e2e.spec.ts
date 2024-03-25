/* eslint-disable prefer-promise-reject-errors */
import request from 'supertest';
import type { INestApplication } from '@nestjs/common';
import { HttpStatus, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { MongooseModule } from '@nestjs/mongoose';
import type { StartedTestContainer } from 'testcontainers';
import { GenericContainer, Wait } from 'testcontainers';
import { APP_PIPE } from '@nestjs/core';
import type { Analysis } from '@privateaim/core';
import { APIClient } from '@privateaim/core';
import { MessageSubscriptionModule } from '../../../../../src/server/message/subscription/subscription.module';
import { AuthGuard } from '../../../../../src/server/auth/auth.guard';

const MONGO_DB_TEST_DB_NAME: string = 'message-broker-subscriptions-test';

describe('Message Subscription Module', () => {
    let mongodbEnv: StartedTestContainer;
    let app: INestApplication;
    let hubApiClient: APIClient;

    beforeAll(async () => {
        mongodbEnv = await new GenericContainer('mongo:7.0.5@sha256:fcde2d71bf00b592c9cabab1d7d01defde37d69b3d788c53c3bc7431b6b15de8')
            .withExposedPorts(27017)
            .withEnvironment({
                MONGO_INITDB_DATABASE: MONGO_DB_TEST_DB_NAME,
            })
            .withWaitStrategy(Wait.forSuccessfulCommand('echo \'db.runCommand("ping").ok\' | mongosh localhost:27017/test --quiet'))
            .start();

        const mongoDbConnString = `mongodb://${mongodbEnv.getHost()}:${mongodbEnv.getFirstMappedPort()}/`;

        hubApiClient = new APIClient({});

        const moduleRef = await Test.createTestingModule({
            imports: [
                MongooseModule.forRootAsync({
                    useFactory: async () => ({
                        uri: mongoDbConnString,
                        dbName: MONGO_DB_TEST_DB_NAME,
                    }),
                }),
                MessageSubscriptionModule,
            ],
            providers: [
                {
                    provide: APP_PIPE,
                    useClass: ValidationPipe,
                },
            ],
        })
            .overrideGuard(AuthGuard)
            .useValue({ canActivate: () => true })
            .overrideProvider(APIClient)
            .useValue(hubApiClient)
            .compile();

        app = moduleRef.createNestApplication();
        await app.init();
    }, 300000); // timeout takes into account that this image might have to be pulled first

    beforeEach(() => {
        jest.clearAllMocks();
    });

    afterAll(async () => {
        await app.close();
        await mongodbEnv.stop();
    });

    it('/POST subscriptions should persist a new subscription', async () => {
        const testAnalysisId = 'd985ddb4-e0af-407f-afd0-6d002813d29c';
        const testWebhookUrl = 'http://localhost/bar';

        jest.spyOn(hubApiClient.analysis, 'getOne').mockImplementation(() => Promise.resolve(jest.fn() as unknown as Analysis));

        const subscriptionId = await request(app.getHttpServer())
            .post(`/analyses/${testAnalysisId}/messages/subscriptions`)
            .send({ webhookUrl: testWebhookUrl })
            .expect(HttpStatus.CREATED)
            .then((res) => {
                const { location } = res.header;
                const { subscriptionId } = res.body;
                expect(location.endsWith(subscriptionId)).toBeTruthy();

                return subscriptionId;
            });

        await request(app.getHttpServer())
            .get(`/analyses/${testAnalysisId}/messages/subscriptions/${subscriptionId}`)
            .expect(HttpStatus.OK)
            .then((res) => {
                expect(res.body.id).toBe(subscriptionId);
                expect(res.body.analysisId).toBe(testAnalysisId);
                expect(res.body.webhookUrl).toBe(testWebhookUrl);
            });
    });

    it.each([
        [''],
        ['not:a-domain'],
    ])('/POST subscriptions should return an error on malformed body', async (webhookUrl) => {
        await request(app.getHttpServer())
            .post('/analyses/foo/messages/subscriptions')
            .send({ webhookUrl })
            .expect(HttpStatus.BAD_REQUEST);
    });

    it('/POST subscriptions returns 404 if analysis does not exist', async () => {
        const testAnalysisId = 'b2b1a935-3f9f-452d-83c2-d5a21a5cd616';
        const testWebhookUrl = 'http://localhost/bar';

        jest.spyOn(hubApiClient.analysis, 'getOne').mockImplementation(() => Promise.reject({
            statusCode: 404,
        }));

        await request(app.getHttpServer())
            .post(`/analyses/${testAnalysisId}/messages/subscriptions`)
            .send({ webhookUrl: testWebhookUrl })
            .expect(HttpStatus.NOT_FOUND);
    });

    it.each([
        [500],
        [501],
        [502],
        [503],
    ])('/POST subscriptions returns 502 if central side ', async (httpStatusCode) => {
        const testAnalysisId = 'b2b1a935-3f9f-452d-83c2-d5a21a5cd616';
        const testWebhookUrl = 'http://localhost/bar';

        jest.spyOn(hubApiClient.analysis, 'getOne').mockImplementation(() => Promise.reject({
            statusCode: httpStatusCode,
        }));

        await request(app.getHttpServer())
            .post(`/analyses/${testAnalysisId}/messages/subscriptions`)
            .send({ webhookUrl: testWebhookUrl })
            .expect(HttpStatus.BAD_GATEWAY);
    });
});
