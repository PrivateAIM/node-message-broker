import axios from 'axios';
import nock from 'nock';
import { MessageNodeEmitterError, MessageNodeEmitterService } from '../../../../../src/server/message/node/node.emitter.service';

describe('Message Node Emitter Service', () => {
    let service: MessageNodeEmitterService;

    beforeAll(async () => {
        // Necessary for axios to work with nock.
        // For more information, see: https://github.com/nock/nock?tab=readme-ov-file#axios
        axios.defaults.adapter = 'http';
        // Make sure to disable real connections to all non-mocked receivers.
        nock.disableNetConnect();

        service = MessageNodeEmitterService.create();
    });

    afterAll(async () => {
        nock.restore();
    });

    it('should emit a message to the receiver using POST', async () => {
        const scope = nock('http://localhost')
            .post('/test/my-webhook-url')
            .reply(204);

        const result = await service.emitMessage({ webhookUrl: new URL('http://localhost/test/my-webhook-url') }, { foo: 'bar' });
        expect(result).toBeUndefined();

        scope.done();
    });

    it.each([
        [404, MessageNodeEmitterError.RECEIVER_REJECTED_REQUEST, {}],
        [500, MessageNodeEmitterError.RECEIVER_WITH_INTERNAL_ERROR, { foo: 'bar' }],
        [503, MessageNodeEmitterError.RECEIVER_UNAVAILABLE, { foo: 'bar' }],
        [501, MessageNodeEmitterError.UNKNOWN, { foo: 'bar' }],
    ])('should return an error if emitting the message fails', async (responseCode, expectedError, data) => {
        let registeredRequestBody;
        const scope = nock('http://localhost')
            .post('/test/my-webhook-url', (body) => {
                registeredRequestBody = body;
                return body;
            })
            .reply(responseCode);

        await service.emitMessage({ webhookUrl: new URL('http://localhost/test/my-webhook-url') }, data)
            .catch((res) => {
                expect(res).toBe(expectedError);
            });
        expect(registeredRequestBody).toStrictEqual(data);

        scope.done();
    });
});
