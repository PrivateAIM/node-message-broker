import request from 'supertest';
import { Test } from '@nestjs/testing';
import { HttpStatus, type INestApplication } from '@nestjs/common';
import { when } from 'jest-when';
import { APIClient } from '@authup/core';
import { DISCOVERY_SERVICE, DiscoveryError, NodeType } from '../../../../src/server/discovery/discovery.service';
import type { DiscoveryService } from '../../../../src/server/discovery/discovery.service';
import { AuthGuard } from '../../../../src/server/auth/auth.guard';
import { DiscoveryController } from '../../../../src/server/discovery/discovery.controller';

describe('Discovery Controller', () => {
    const mockedDiscoveryService: DiscoveryService = {
        discoverParticipatingAnalysisNodes: jest.fn(),
        discoverSelf: jest.fn(),
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
            .expect(HttpStatus.OK)
            .then((res) => {
                expect(res.body).toHaveLength(2);
                expect(res.body[0].nodeId).toBe('foo');
                expect(res.body[0].nodeType).toBe(NodeType.AGGREGATOR);
                expect(res.body[1].nodeId).toBe('bar');
                expect(res.body[1].nodeType).toBe(NodeType.DEFAULT);
            });
    });

    it('/GET participants should return 502 if participating analysis nodes cannot be fetched from central side', async () => {
        const testAnalysisId: string = '7e8cd08c-f536-4a44-bbf3-7d235e9cebc9';

        when(mockedDiscoveryService.discoverParticipatingAnalysisNodes).calledWith(testAnalysisId)
            .mockRejectedValue(new DiscoveryError({
                name: 'FAILED_TO_FETCH_ANALYSIS_NODES',
                message: 'something describing the error',
            }));

        await request(app.getHttpServer())
            .get(`/analyses/${testAnalysisId}/participants`)
            .expect(HttpStatus.BAD_GATEWAY);
    });

    it('/GET self should get participation information about the message broker itself from the underlying service', async () => {
        const testAnalysisId: string = '7e8cd08c-f536-4a44-bbf3-7d235e9cebc9';

        when(mockedDiscoveryService.discoverSelf).calledWith(testAnalysisId)
            .mockResolvedValue({
                nodeId: 'foo',
                nodeType: NodeType.DEFAULT,
            });

        await request(app.getHttpServer())
            .get(`/analyses/${testAnalysisId}/participants/self`)
            .expect(HttpStatus.OK)
            .then((res) => {
                expect(res.body).toBeInstanceOf(Object);
                expect(res.body.nodeId).toBe('foo');
                expect(res.body.nodeType).toBe(NodeType.DEFAULT);
            });
    });

    it('/GET self should return 404 if self cannot be found', async () => {
        const testAnalysisId: string = '7e8cd08c-f536-4a44-bbf3-7d235e9cebc9';

        when(mockedDiscoveryService.discoverSelf).calledWith(testAnalysisId)
            .mockRejectedValue(
                new DiscoveryError({
                    name: 'SELF_NOT_FOUND',
                    message: 'something describing the error',
                }),
            );

        await request(app.getHttpServer())
            .get(`/analyses/${testAnalysisId}/participants/self`)
            .expect(HttpStatus.NOT_FOUND);
    });

    it('/GET self should return 502 if participating analysis nodes cannot be fetched from central side', async () => {
        const testAnalysisId: string = '7e8cd08c-f536-4a44-bbf3-7d235e9cebc9';

        when(mockedDiscoveryService.discoverSelf).calledWith(testAnalysisId)
            .mockRejectedValue(
                new DiscoveryError({
                    name: 'FAILED_TO_FETCH_ANALYSIS_NODES',
                    message: 'something describing the error',
                }),
            );

        await request(app.getHttpServer())
            .get(`/analyses/${testAnalysisId}/participants/self`)
            .expect(HttpStatus.BAD_GATEWAY);
    });
});
