import request from 'supertest';
import type { INestApplication } from '@nestjs/common';
import { ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { MongooseModule } from '@nestjs/mongoose';
import type { StartedTestContainer } from 'testcontainers';
import { GenericContainer, Wait } from 'testcontainers';
import { APP_PIPE } from '@nestjs/core';
import { MessageSubscriptionModule } from '../../../../../src/server/message/subscription/subscription.module';
import { AuthGuard } from '../../../../../src/server/auth/auth.guard';

const MONGO_DB_TEST_DB_NAME: string = 'message-broker-subscriptions-test';

describe('Message Subscription Module', () => {
    let mongodbEnv: StartedTestContainer;
    let app: INestApplication;

    beforeAll(async () => {
        mongodbEnv = await new GenericContainer('mongo:7.0.5@sha256:fcde2d71bf00b592c9cabab1d7d01defde37d69b3d788c53c3bc7431b6b15de8')
            .withExposedPorts(27017)
            .withEnvironment({
                MONGO_INITDB_DATABASE: MONGO_DB_TEST_DB_NAME,
            })
            .withWaitStrategy(Wait.forSuccessfulCommand('echo \'db.runCommand("ping").ok\' | mongosh localhost:27017/test --quiet'))
            .start();

        const mongoDbConnString = `mongodb://${mongodbEnv.getHost()}:${mongodbEnv.getFirstMappedPort()}/`;

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
            .compile();

        app = moduleRef.createNestApplication();
        await app.init();
    });

    afterAll(async () => {
        await app.close();
        await mongodbEnv.stop();
    });

    it('/POST subscriptions should persist a new subscription', async () => {
        const testAnalysisId = 'foo';
        const testWebhookUrl = 'http://localhost/bar';

        const subscriptionId = await request(app.getHttpServer())
            .post('/messages/subscriptions')
            .send({ analysisId: testAnalysisId, webhookUrl: testWebhookUrl })
            .expect(201)
            .then((res) => {
                const { location } = res.header;
                const { subscriptionId } = res.body;
                expect(location.endsWith(subscriptionId)).toBeTruthy();

                return subscriptionId;
            });

        await request(app.getHttpServer())
            .get(`/messages/subscriptions/${subscriptionId}`)
            .expect(200)
            .then((res) => {
                expect(res.body.id).toBe(subscriptionId);
                expect(res.body.analysisId).toBe(testAnalysisId);
                expect(res.body.webhookUrl).toBe(testWebhookUrl);
            });
    });

    it.each([
        ['', 'http://localhost/foo'],
        [1, 'http://localhost/foo'],
        ['analysis-1', ''],
        ['analysis-1', 'not:a-domain'],
    ])('/POST subscriptions should return an error on malformed body', async (analysisId, webhookUrl) => {
        await request(app.getHttpServer())
            .post('/messages/subscriptions')
            .send({ analysisId, webhookUrl })
            .expect(400);
    });
});
