import type { Analysis, Registry, RegistryProject } from '@privateaim/core';
import { APIClient, AnalysisNodeApprovalStatus } from '@privateaim/core';
import type { DiscoveryService } from '../../../../src/server/discovery/discovery.service';
import { DiscoveryError, HubBackedDiscoveryService, NodeType } from '../../../../src/server/discovery/discovery.service';

describe('DiscoveryService', () => {
    const nodeId = '62549195-508c-43d2-acdd-fc61a51da7cf';

    let hubApiClient: APIClient;
    let service: DiscoveryService;

    beforeAll(() => {
        hubApiClient = new APIClient({});
        service = new HubBackedDiscoveryService(hubApiClient, nodeId);
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    describe('discover participating analysis nodes', () => {
        it('should return empty array if analysis cannot be found', async () => {
            jest.spyOn(hubApiClient.analysisNode, 'getMany').mockImplementation(() => Promise.resolve({
                data: [],
                meta: {
                    total: 0,
                    limit: 0,
                    offset: 0,
                },
            }));

            await expect(service.discoverParticipatingAnalysisNodes('analysis-id-foo')).resolves.toStrictEqual([]);
        });

        it('should ignore analysis nodes that are not associated with a robot id', async () => {
            jest.spyOn(hubApiClient.analysisNode, 'getMany').mockImplementation(() => Promise.resolve({
                data: [
                    {
                        id: '41e90668-e014-43ed-b3f2-5e33f6ffc83e',
                        analysis_id: '8b808d0e-716a-49e0-acbc-0c84caedb093',
                        analysis_realm_id: '749bf6d8-abcc-4a66-ac41-b9c4c83f217d',
                        node_id: 'a99dea83-cf86-40e1-8578-d578f98164c4',
                        node_realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                        approval_status: AnalysisNodeApprovalStatus.APPROVED,
                        index: 0,
                        run_status: null,
                        artifact_tag: null,
                        artifact_digest: null,
                        comment: '',
                        analysis: jest.fn() as unknown as Analysis,
                        created_at: new Date(Date.now()),
                        updated_at: new Date(Date.now()),
                        node: {
                            id: 'bff2cb92-7849-47b0-bb77-f1632464cf18',
                            robot_id: 'foo',
                            created_at: new Date(Date.now()),
                            updated_at: new Date(Date.now()),
                            external_name: 'foo',
                            email: null,
                            hidden: true,
                            name: 'foo',
                            online: false,
                            realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                            registry: jest.fn() as unknown as Registry,
                            registry_id: '',
                            registry_project: jest.fn() as unknown as RegistryProject,
                            registry_project_id: '',
                            type: 'aggregator',
                        },
                    },
                    {
                        id: '71ebe269-f9d8-4576-a4e2-b1ce3c17ce64',
                        analysis_id: '8b808d0e-716a-49e0-acbc-0c84caedb093',
                        analysis_realm_id: '749bf6d8-abcc-4a66-ac41-b9c4c83f217d',
                        node_id: '47ef1dc1-abe1-456c-a967-d985309caf2f',
                        node_realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                        approval_status: AnalysisNodeApprovalStatus.APPROVED,
                        index: 0,
                        run_status: null,
                        artifact_tag: null,
                        artifact_digest: null,
                        comment: '',
                        analysis: jest.fn() as unknown as Analysis,
                        created_at: new Date(Date.now()),
                        updated_at: new Date(Date.now()),
                        node: {
                            id: '47ef1dc1-abe1-456c-a967-d985309caf2f',
                            robot_id: null,
                            created_at: new Date(Date.now()),
                            updated_at: new Date(Date.now()),
                            external_name: 'bar',
                            email: null,
                            hidden: true,
                            name: 'bar',
                            online: false,
                            realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                            registry: jest.fn() as unknown as Registry,
                            registry_id: '',
                            registry_project: jest.fn() as unknown as RegistryProject,
                            registry_project_id: '',
                            type: 'default',
                        },
                    },
                ],
                meta: {
                    total: 2,
                    limit: 2,
                    offset: 0,
                },
            }));

            await expect(service.discoverParticipatingAnalysisNodes('8b808d0e-716a-49e0-acbc-0c84caedb093')).resolves.toStrictEqual([{
                nodeId: 'foo',
                nodeType: NodeType.AGGREGATOR,
            }]);
        });

        it('should map all analysis nodes that are associated with a robot id', async () => {
            jest.spyOn(hubApiClient.analysisNode, 'getMany').mockImplementation(() => Promise.resolve({
                data: [
                    {
                        id: '41e90668-e014-43ed-b3f2-5e33f6ffc83e',
                        analysis_id: '8b808d0e-716a-49e0-acbc-0c84caedb093',
                        analysis_realm_id: '749bf6d8-abcc-4a66-ac41-b9c4c83f217d',
                        node_id: 'a99dea83-cf86-40e1-8578-d578f98164c4',
                        node_realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                        approval_status: AnalysisNodeApprovalStatus.APPROVED,
                        index: 0,
                        run_status: null,
                        artifact_tag: null,
                        artifact_digest: null,
                        comment: '',
                        analysis: jest.fn() as unknown as Analysis,
                        created_at: new Date(Date.now()),
                        updated_at: new Date(Date.now()),
                        node: {
                            id: 'bff2cb92-7849-47b0-bb77-f1632464cf18',
                            robot_id: 'foo',
                            created_at: new Date(Date.now()),
                            updated_at: new Date(Date.now()),
                            external_name: 'foo',
                            email: null,
                            hidden: true,
                            name: 'foo',
                            online: false,
                            realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                            registry: jest.fn() as unknown as Registry,
                            registry_id: '',
                            registry_project: jest.fn() as unknown as RegistryProject,
                            registry_project_id: '',
                            type: 'aggregator',
                        },
                    },
                    {
                        id: '71ebe269-f9d8-4576-a4e2-b1ce3c17ce64',
                        analysis_id: '8b808d0e-716a-49e0-acbc-0c84caedb093',
                        analysis_realm_id: '749bf6d8-abcc-4a66-ac41-b9c4c83f217d',
                        node_id: '47ef1dc1-abe1-456c-a967-d985309caf2f',
                        node_realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                        approval_status: AnalysisNodeApprovalStatus.APPROVED,
                        index: 0,
                        run_status: null,
                        artifact_tag: null,
                        artifact_digest: null,
                        comment: '',
                        analysis: jest.fn() as unknown as Analysis,
                        created_at: new Date(Date.now()),
                        updated_at: new Date(Date.now()),
                        node: {
                            id: '47ef1dc1-abe1-456c-a967-d985309caf2f',
                            robot_id: 'bar',
                            created_at: new Date(Date.now()),
                            updated_at: new Date(Date.now()),
                            external_name: 'bar',
                            email: null,
                            hidden: true,
                            name: 'bar',
                            online: false,
                            realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                            registry: jest.fn() as unknown as Registry,
                            registry_id: '',
                            registry_project: jest.fn() as unknown as RegistryProject,
                            registry_project_id: '',
                            type: 'default',
                        },
                    },
                ],
                meta: {
                    total: 2,
                    limit: 2,
                    offset: 0,
                },
            }));

            await expect(service.discoverParticipatingAnalysisNodes('8b808d0e-716a-49e0-acbc-0c84caedb093')).resolves.toStrictEqual([
                { nodeId: 'foo', nodeType: NodeType.AGGREGATOR },
                { nodeId: 'bar', nodeType: NodeType.DEFAULT },
            ]);
        });

        it('should fail if the downstream communication to the central side (hub) fails', async () => {
            jest.spyOn(hubApiClient.analysisNode, 'getMany').mockImplementation(() => Promise.reject(
                new Error('some downstream server error'),
            ));

            await expect(service.discoverParticipatingAnalysisNodes('some-analysis-id')).rejects.toBeInstanceOf(DiscoveryError);
        });
    });

    describe('discover self', () => {
        it('should fail if there are no analysis nodes at all', async () => {
            jest.spyOn(hubApiClient.analysisNode, 'getMany').mockImplementation(() => Promise.resolve({
                data: [],
                meta: {
                    total: 0,
                    limit: 0,
                    offset: 0,
                },
            }));

            await expect(service.discoverSelf('some-analysis-id')).rejects.toBeInstanceOf(DiscoveryError);
        });

        it('should fail if self is not part of the returned participating analysis nodes', async () => {
            jest.spyOn(hubApiClient.analysisNode, 'getMany').mockImplementation(() => Promise.resolve({
                data: [
                    {
                        id: '41e90668-e014-43ed-b3f2-5e33f6ffc83e',
                        analysis_id: '8b808d0e-716a-49e0-acbc-0c84caedb093',
                        analysis_realm_id: '749bf6d8-abcc-4a66-ac41-b9c4c83f217d',
                        node_id: 'a99dea83-cf86-40e1-8578-d578f98164c4',
                        node_realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                        approval_status: AnalysisNodeApprovalStatus.APPROVED,
                        index: 0,
                        run_status: null,
                        artifact_tag: null,
                        artifact_digest: null,
                        comment: '',
                        analysis: jest.fn() as unknown as Analysis,
                        created_at: new Date(Date.now()),
                        updated_at: new Date(Date.now()),
                        node: {
                            id: 'bff2cb92-7849-47b0-bb77-f1632464cf18',
                            robot_id: 'foo',
                            created_at: new Date(Date.now()),
                            updated_at: new Date(Date.now()),
                            external_name: 'foo',
                            email: null,
                            hidden: true,
                            name: 'foo',
                            online: false,
                            realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                            registry: jest.fn() as unknown as Registry,
                            registry_id: '',
                            registry_project: jest.fn() as unknown as RegistryProject,
                            registry_project_id: '',
                            type: 'aggregator',
                        },
                    },
                    {
                        id: '71ebe269-f9d8-4576-a4e2-b1ce3c17ce64',
                        analysis_id: '8b808d0e-716a-49e0-acbc-0c84caedb093',
                        analysis_realm_id: '749bf6d8-abcc-4a66-ac41-b9c4c83f217d',
                        node_id: '47ef1dc1-abe1-456c-a967-d985309caf2f',
                        node_realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                        approval_status: AnalysisNodeApprovalStatus.APPROVED,
                        index: 0,
                        run_status: null,
                        artifact_tag: null,
                        artifact_digest: null,
                        comment: '',
                        analysis: jest.fn() as unknown as Analysis,
                        created_at: new Date(Date.now()),
                        updated_at: new Date(Date.now()),
                        node: {
                            id: '47ef1dc1-abe1-456c-a967-d985309caf2f',
                            robot_id: null,
                            created_at: new Date(Date.now()),
                            updated_at: new Date(Date.now()),
                            external_name: 'bar',
                            email: null,
                            hidden: true,
                            name: 'bar',
                            online: false,
                            realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                            registry: jest.fn() as unknown as Registry,
                            registry_id: '',
                            registry_project: jest.fn() as unknown as RegistryProject,
                            registry_project_id: '',
                            type: 'default',
                        },
                    },
                ],
                meta: {
                    total: 2,
                    limit: 2,
                    offset: 0,
                },
            }));

            const result = service.discoverSelf('8b808d0e-716a-49e0-acbc-0c84caedb093');
            await expect(result).rejects.toBeInstanceOf(DiscoveryError);
            await expect(result).rejects.toHaveProperty('name', 'SELF_NOT_FOUND');
        });

        it('should only return self if it is part of the returned participating analysis nodes', async () => {
            jest.spyOn(hubApiClient.analysisNode, 'getMany').mockImplementation(() => Promise.resolve({
                data: [
                    {
                        id: '41e90668-e014-43ed-b3f2-5e33f6ffc83e',
                        analysis_id: '8b808d0e-716a-49e0-acbc-0c84caedb093',
                        analysis_realm_id: '749bf6d8-abcc-4a66-ac41-b9c4c83f217d',
                        node_id: 'a99dea83-cf86-40e1-8578-d578f98164c4',
                        node_realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                        approval_status: AnalysisNodeApprovalStatus.APPROVED,
                        index: 0,
                        run_status: null,
                        artifact_tag: null,
                        artifact_digest: null,
                        comment: '',
                        analysis: jest.fn() as unknown as Analysis,
                        created_at: new Date(Date.now()),
                        updated_at: new Date(Date.now()),
                        node: {
                            id: 'bff2cb92-7849-47b0-bb77-f1632464cf18',
                            robot_id: 'foo',
                            created_at: new Date(Date.now()),
                            updated_at: new Date(Date.now()),
                            external_name: 'foo',
                            email: null,
                            hidden: true,
                            name: 'foo',
                            online: false,
                            realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                            registry: jest.fn() as unknown as Registry,
                            registry_id: '',
                            registry_project: jest.fn() as unknown as RegistryProject,
                            registry_project_id: '',
                            type: 'aggregator',
                        },
                    },
                    {
                        id: '71ebe269-f9d8-4576-a4e2-b1ce3c17ce64',
                        analysis_id: '8b808d0e-716a-49e0-acbc-0c84caedb093',
                        analysis_realm_id: '749bf6d8-abcc-4a66-ac41-b9c4c83f217d',
                        node_id: '47ef1dc1-abe1-456c-a967-d985309caf2f',
                        node_realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                        approval_status: AnalysisNodeApprovalStatus.APPROVED,
                        index: 0,
                        run_status: null,
                        artifact_tag: null,
                        artifact_digest: null,
                        comment: '',
                        analysis: jest.fn() as unknown as Analysis,
                        created_at: new Date(Date.now()),
                        updated_at: new Date(Date.now()),
                        node: {
                            id: '47ef1dc1-abe1-456c-a967-d985309caf2f',
                            robot_id: '62549195-508c-43d2-acdd-fc61a51da7cf',
                            created_at: new Date(Date.now()),
                            updated_at: new Date(Date.now()),
                            external_name: 'bar',
                            email: null,
                            hidden: true,
                            name: 'bar',
                            online: false,
                            realm_id: 'd5850f01-be93-4b6d-a543-11a2886e33ea',
                            registry: jest.fn() as unknown as Registry,
                            registry_id: '',
                            registry_project: jest.fn() as unknown as RegistryProject,
                            registry_project_id: '',
                            type: 'default',
                        },
                    },
                ],
                meta: {
                    total: 2,
                    limit: 2,
                    offset: 0,
                },
            }));

            await expect(service.discoverSelf('8b808d0e-716a-49e0-acbc-0c84caedb093')).resolves.toStrictEqual({
                nodeId: '62549195-508c-43d2-acdd-fc61a51da7cf',
                nodeType: NodeType.DEFAULT,
            });
        });
    });
});
