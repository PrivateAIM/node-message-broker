FROM eclipse-temurin:21-jre-alpine@sha256:2a0bbb1db6d8db42c66ed00c43d954cf458066cc37be12b55144781da7864fdf

ARG USERNAME=mb-user
ARG USER_UID=1001
ARG USER_GID=$USER_UID

WORKDIR /opt/node-message-broker

RUN addgroup --gid $USER_GID $USERNAME \
    && adduser --disabled-password --no-create-home --gecos "" --uid $USER_UID --ingroup $USERNAME $USERNAME \
    && chown -R $USER_UID:$USER_GID /opt/node-message-broker \
    && apk --no-cache add curl bash

USER $USER_UID

HEALTHCHECK --interval=5s --start-period=10s CMD curl -s -f http://localhost:8080/actuator/health || exit 1

COPY target/node-message-broker*.jar ./broker.jar

ENTRYPOINT ["java", "-jar", "broker.jar"]
