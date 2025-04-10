package messagebroker

import (
	"net/http"
	"time"
)

type subscriptionAPI interface {
	CreateSubscription(analysisId string, webhookUrl string) (SubscriptionId, error)
	DeleteSubscription(analysisId string, subscriptionId SubscriptionId) error
}

type discoveryAPI interface {
	DiscoverOwnNodeId(analysisId string) (NodeId, error)
}

type messageAPI interface {
	SendMessage(analysisId string, recipientId NodeId, message TestMessage) error
	SendBroadcastMessage(analysisId string, message TestMessage) error
}

type intance interface {
	GetBaseUrl() string
}

type MessageBrokerClient interface {
	subscriptionAPI
	discoveryAPI
	messageAPI
	intance
}

type messageBrokerClient struct {
	baseUrl             string
	accessTokenAcquirer func() (string, error)
	httpClient          http.Client
}

func (mbc *messageBrokerClient) GetBaseUrl() string {
	return mbc.baseUrl
}

func NewMessageBrokerClient(baseUrl string, accessTokenAcquirer func() (string, error)) MessageBrokerClient {
	return &messageBrokerClient{
		baseUrl:             baseUrl,
		accessTokenAcquirer: accessTokenAcquirer,
		httpClient:          http.Client{Timeout: time.Duration(1) * time.Second},
	}
}
