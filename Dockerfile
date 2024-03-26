FROM node:20.11-alpine3.19@sha256:c0a3badbd8a0a760de903e00cedbca94588e609299820557e72cba2a53dbaa2c
RUN mkdir -p /home/node/node-message-broker/node_modules && chown -R node:node /home/node/node-message-broker
WORKDIR /home/node/node-message-broker
COPY --chown=node:node package*.json .

USER node

RUN npm install

COPY --chown=node:node ./dist .

EXPOSE 3000

CMD [ "node", "index.cjs" ]
