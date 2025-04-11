package cmd

import (
	"fmt"
	messagebroker "mb-test-cli/message-broker"
	"sort"

	log "github.com/sirupsen/logrus"
)

func configureReceiverNodeSubscription(receiverNodeClient messagebroker.MessageBrokerClient, analysisId string, webhookUrl string) (messagebroker.SubscriptionId, error) {
	subscription, err := receiverNodeClient.CreateSubscription(analysisId, webhookUrl)
	if err != nil {
		return "", fmt.Errorf("could not create subscription for receiver node: %v", err)
	}
	return subscription, nil
}

func cleanUpReceiverNodeSubscription(receiverNodeClient messagebroker.MessageBrokerClient, analysisId string, subscriptionId messagebroker.SubscriptionId) {
	if err := receiverNodeClient.DeleteSubscription(analysisId, subscriptionId); err != nil {
		log.Fatalf("could not delete subscription with id `%s` for receiver node at `%s`: %v", subscriptionId, receiverNodeClient.GetBaseUrl(), err)
	}
}

func SortMessages(messages []messagebroker.TestMessage) {
	sort.Slice(messages, func(i, j int) bool {
		return messages[i].Id < messages[j].Id
	})
}
