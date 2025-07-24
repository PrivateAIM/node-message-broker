package cmd

import (
	"fmt"
	messagebroker "mb-test-cli/message-broker"
	"sort"
	"sync"
	"time"

	log "github.com/sirupsen/logrus"
)

func waitForTestNodesToBeReady(clients []messagebroker.MessageBrokerClient, timeout time.Duration) error {
	var readiness sync.WaitGroup
	readiness.Add(len(clients))

	for _, client := range clients {
		go func() {
			defer readiness.Done()

			for {
				ready, err := client.IsReady()
				if err != nil {
					log.Warn("could not check readiness for broker at `%s`", client.GetBaseUrl())
					continue
				}
				if !ready {
					continue
				}
				break
			}
		}()
	}

	completionCh := make(chan struct{})
	go func() {
		defer close(completionCh)
		readiness.Wait()
	}()

	select {
	case <-completionCh:
		return nil
	case <-time.After(timeout):
		return fmt.Errorf("timed out while waiting for test nodes to get ready")
	}
}

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
