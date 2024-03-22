import request from 'supertest';
import { Test } from '@nestjs/testing';
import type { INestApplication } from '@nestjs/common';
import { when } from 'jest-when';
import { APIClient } from '@authup/core';
import { DISCOVERY_SERVICE, NodeType } from '../../../../src/server/discovery/discovery.service';
import type { DiscoveryService } from '../../../../src/server/discovery/discovery.service';
import { AuthGuard } from '../../../../src/server/auth/auth.guard';
import { DiscoveryController } from '../../../../src/server/discovery/discovery.controller';

describe('Discovery Controller', () => {
    const mockedDiscoveryService: DiscoveryService = {
        discoverParticipatingAnalysisNodes: jest.fn(),
    };
    let app: INestApplication;

    beforeAll(async () => {
        const moduleRef = await Test.createTestingModule({
            controllers: [
                DiscoveryController,
            ],
            providers: [
                {
                    provide: DISCOVERY_SERVICE,
                    useValue: mockedDiscoveryService,
                },
                {
                    provide: APIClient,
                    useValue: jest.fn(),
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
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('/GET participants should get all participating analysis nodes from the underlying service', async () => {
        const testAnalysisId: string = '7e8cd08c-f536-4a44-bbf3-7d235e9cebc9';

        when(mockedDiscoveryService.discoverParticipatingAnalysisNodes).calledWith(testAnalysisId)
            .mockResolvedValue([
                {
                    nodeId: 'foo',
                    nodeType: NodeType.AGGREGATOR,
                },
                {
                    nodeId: 'bar',
                    nodeType: NodeType.DEFAULT,
                },
            ]);

        await request(app.getHttpServer())
            .get(`/analyses/${testAnalysisId}/participants`)
            .expect(200)
            .then((res) => {
                expect(res.body).toHaveLength(2);
                expect(res.body[0].nodeId).toBe('foo');
                expect(res.body[0].nodeType).toBe(NodeType.AGGREGATOR);
                expect(res.body[1].nodeId).toBe('bar');
                expect(res.body[1].nodeType).toBe(NodeType.DEFAULT);
            });
    });
});
