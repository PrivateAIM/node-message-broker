FROM golang:1.23-alpine3.21@sha256:e438c135c348bd7677fde18d1576c2f57f265d5dfa1a6b26fca975d4aa40b3bb

RUN apk add --update \
    curl \
    && rm -rf /var/cache/apk/*

WORKDIR /app
COPY app/go.mod app/go.sum ./
RUN go mod download

COPY app/*.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -o /server

EXPOSE 9000

CMD ["/server"]


