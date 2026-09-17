FROM eclipse-temurin:25-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682

ARG USERNAME=mb-user
ARG USER_UID=1001
ARG USER_GID=$USER_UID

WORKDIR /opt/node-message-broker

RUN addgroup --gid $USER_GID $USERNAME \
    && adduser --disabled-password --no-create-home --gecos "" --uid $USER_UID --ingroup $USERNAME $USERNAME \
    && chown -R $USER_UID:$USER_GID /opt/node-message-broker \
    && apk --no-cache add curl bash

USER $USER_UID

ENV MANAGEMENT_SERVER_PORT=8090
HEALTHCHECK --interval=5s --start-period=10s CMD curl -s -f http://localhost:${MANAGEMENT_SERVER_PORT}/actuator/health || exit 1

COPY target/node-message-broker*.jar ./broker.jar

ENTRYPOINT ["java", "-jar", "broker.jar"]
