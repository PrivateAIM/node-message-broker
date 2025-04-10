package cmd

import (
	"fmt"
	messagebroker "mb-test-cli/message-broker"
	"mb-test-cli/util"
	"sort"
	"sync"
	"time"

	log "github.com/sirupsen/logrus"
	"github.com/spf13/cobra"
)

const numberOfMessagesToSend = 10

var (
	sendDedicatedMessagesTestCmd = &cobra.Command{
		Use:   "send-dedicated-messages",
		Short: "Runs a system test where messages are sent to dedicated recipients",
		RunE:  run,
	}
)

type TestNodeClients struct {
	SenderNodeClient   messagebroker.MessageBrokerClient
	ReceiverNodeClient messagebroker.MessageBrokerClient
}

func run(_ *cobra.Command, _ []string) error {
	log.SetFormatter(&log.TextFormatter{
		FullTimestamp: true,
	})
	log.Info("Running system test for sending messages to dedicated recipients")
	log.Infof("Running test case with analysis ID: `%s`", analysisId)
	log.Infof("Running test case with bootstrap nodes: `%s`", bootstrapNodes)

	if len(bootstrapNodes) < 2 {
		return fmt.Errorf("at least two bootstrap nodes have to be specified")
	}

	authClient := messagebroker.NewMessageBrokerAuthClient(nodeAuthBaseUrl, nodeAuthClientId, nodeAuthClientSecret)
	testNodeClients := getTestNodeClients(bootstrapNodes, authClient)

	log.Infof("setting up subscription for result server at reciever node with base URL: `%s`", testNodeClients.ReceiverNodeClient.GetBaseUrl())
	// TODO: remove hard coded value
	subscriptionId, err := configureReceiverNodeSubscription(testNodeClients.ReceiverNodeClient, "http://host.docker.internal:8080/results")
	if err != nil {
		return fmt.Errorf("could not configure receiver node")
	}
	defer func() {
		log.Info("cleaning up receiver node subscription")
		cleanUpReceiverNodeSubscription(testNodeClients.ReceiverNodeClient, analysisId, subscriptionId)
	}()

	log.Info("Starting result server for receiving messages via subscriptions")
	resultCh := make(chan messagebroker.TestMessage, numberOfMessagesToSend)
	go func() {
		if err := util.RunResultServer(resultCh); err != nil {
			log.Fatalf("failed to start result server: %v", err)
		}
	}()

	log.Infof("creating `%d` random messages to be sent", numberOfMessagesToSend)
	randomMessages, err := util.CreateRandomMessages(numberOfMessagesToSend, 50)
	if err != nil {
		return fmt.Errorf("failed to create random messages: %v", err)
	}
	log.Debugf("created the following messages: `%+q`", randomMessages)

	log.Infof("discovering node ID for receiver node at `%s`", testNodeClients.ReceiverNodeClient.GetBaseUrl())
	nodeId, err := testNodeClients.ReceiverNodeClient.DiscoverOwnNodeId(analysisId)
	if err != nil {
		log.Fatalf("could not discover node ID for receiver node: %v", err)
	}

	log.Info("running send routine")
	var wgSender sync.WaitGroup
	wgSender.Add(1)
	runMessageSendingRoutine(testNodeClients.SenderNodeClient, analysisId, nodeId, randomMessages, &wgSender)

	log.Info("running receive routine")
	receivedMessagesCh := make(chan []messagebroker.TestMessage, 1)
	var wgReceiver sync.WaitGroup
	wgReceiver.Add(1)
	runMessageReceiveRoutine(resultCh, receivedMessagesCh, &wgReceiver)

	log.Info("waiting for sender to finish sending messages")
	wgSender.Wait()
	log.Info("sender finished sending messages")

	log.Info("waiting for receiver to finish receiving messages")
	wgReceiver.Wait()
	log.Infof("receiver finished receiving messages")

	var receivedMessages []messagebroker.TestMessage
	select {
	case messages := <-receivedMessagesCh:
		receivedMessages = messages
	case <-time.After(15 * time.Second):
		log.Fatalf("timed out while reading received messages")
	}

	// sorting since messages may arrive in different order depending on the workload of the message broker
	sortMessages(randomMessages)
	sortMessages(receivedMessages)

	log.Debugf("sent messages (sorted): %+q", randomMessages)
	log.Debugf("received messages (sorted): %+q", receivedMessages)

	if len(randomMessages) != len(receivedMessages) {
		log.Fatalf("number of sent messages (%d) and received messages (%d) differs",
			len(randomMessages), len(receivedMessages))
	}

	for i := 0; i < len(randomMessages); i++ {
		if receivedMessages[i].Id != randomMessages[i].Id {
			log.Fatalf("sent message is not equal to received message")
		}
	}

	log.Info("Success - sent and received messages are equal")

	return nil
}

func getTestNodeClients(bootstrapNodes []string, authClient messagebroker.MessageBrokerAuthClient) TestNodeClients {
	return TestNodeClients{
		SenderNodeClient:   messagebroker.NewMessageBrokerClient(bootstrapNodes[0], authClient.AcquireAccessToken),
		ReceiverNodeClient: messagebroker.NewMessageBrokerClient(bootstrapNodes[1], authClient.AcquireAccessToken),
	}
}

func configureReceiverNodeSubscription(receiverNodeClient messagebroker.MessageBrokerClient, webhookUrl string) (messagebroker.SubscriptionId, error) {
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

func runMessageSendingRoutine(senderNodeClient messagebroker.MessageBrokerClient, analysisId string, targetNodeId messagebroker.NodeId, messages []messagebroker.TestMessage, wg *sync.WaitGroup) {
	go func() {
		defer wg.Done()

		for _, message := range messages {
			err := senderNodeClient.SendMessage(analysisId, targetNodeId, message)
			if err != nil {
				log.Errorf("could not send message: %v", err)
			}
		}
	}()
}

func runMessageReceiveRoutine(resultCh <-chan messagebroker.TestMessage, receivedMessages chan<- []messagebroker.TestMessage, wg *sync.WaitGroup) {
	go func() {
		var results []messagebroker.TestMessage
		defer wg.Done()
		defer close(receivedMessages)

		for {
			select {
			case message := <-resultCh:
				results = append(results, message)
			case <-time.After(15 * time.Second):
				log.Warnf("timed out waiting for incoming messages")
				receivedMessages <- results
				return
			}
		}
	}()
}

func sortMessages(messages []messagebroker.TestMessage) {
	sort.Slice(messages, func(i, j int) bool {
		return messages[i].Id < messages[j].Id
	})
}
