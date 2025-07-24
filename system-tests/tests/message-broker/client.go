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

type healthAPI interface {
	IsReady() (bool, error)
}

type instance interface {
	GetBaseUrl() string
}

type MessageBrokerClient interface {
	subscriptionAPI
	discoveryAPI
	messageAPI
	healthAPI
	instance
}

type messageBrokerClient struct {
	baseUrl             string
	managementBaseUrl   string // base url to reach the broker's management server
	accessTokenAcquirer func() (string, error)
	httpClient          http.Client
}

func (mbc *messageBrokerClient) GetBaseUrl() string {
	return mbc.baseUrl
}

func NewMessageBrokerClient(baseUrl string, managementBaseUrl string, accessTokenAcquirer func() (string, error)) MessageBrokerClient {
	return &messageBrokerClient{
		baseUrl:             baseUrl,
		managementBaseUrl:   managementBaseUrl,
		accessTokenAcquirer: accessTokenAcquirer,
		httpClient:          http.Client{Timeout: time.Duration(300) * time.Second},
	}
}
