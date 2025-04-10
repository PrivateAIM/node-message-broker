package cmd

import (
	"fmt"
	messagebroker "mb-test-cli/message-broker"
	"mb-test-cli/util"
	"sync"
	"time"

	log "github.com/sirupsen/logrus"
	"github.com/spf13/cobra"
)

var (
	sendDedicatedMessagesTestCmd = &cobra.Command{
		Use:   "send-dedicated-messages",
		Short: "Runs a system test where messages are sent to dedicated recipients",
		RunE:  sendDedicatedMessagesFunc,
	}
)

type dedicatedMessagesTestNodeClients struct {
	SenderNodeClient   messagebroker.MessageBrokerClient
	ReceiverNodeClient messagebroker.MessageBrokerClient
}

func sendDedicatedMessagesFunc(cmd *cobra.Command, _ []string) error {
	globalFlags := getGlobalFlags(cmd)

	log.SetFormatter(&log.TextFormatter{
		FullTimestamp: true,
	})
	log.Info("Running system test for sending messages to dedicated recipients")
	log.Infof("Running test case with analysis ID: `%s`", globalFlags.AnalysisId)
	log.Infof("Running test case with bootstrap nodes: `%s`", globalFlags.BootstrapNodes)

	if len(globalFlags.BootstrapNodes) < 2 {
		return fmt.Errorf("at least 2 bootstrap nodes have to be specified")
	}

	authClient := messagebroker.NewMessageBrokerAuthClient(globalFlags.NodeAuthBaseUrl, globalFlags.NodeAuthClientId, globalFlags.NodeAuthClientSecret)
	testNodeClients := getDedicatedMessagesTestNodeClients(globalFlags.BootstrapNodes, authClient)

	log.Infof("setting up subscription for result server at reciever node with base URL: `%s`", testNodeClients.ReceiverNodeClient.GetBaseUrl())
	// TODO: remove hard coded value
	subscriptionId, err := configureReceiverNodeSubscription(testNodeClients.ReceiverNodeClient, globalFlags.AnalysisId, "http://host.docker.internal:8080/results")
	if err != nil {
		return fmt.Errorf("could not configure receiver node")
	}
	defer func() {
		log.Info("cleaning up receiver node subscription")
		cleanUpReceiverNodeSubscription(testNodeClients.ReceiverNodeClient, globalFlags.AnalysisId, subscriptionId)
	}()

	log.Info("Starting result server for receiving messages via subscriptions")
	resultCh := make(chan messagebroker.TestMessage, globalFlags.NumberOfMessagesToSend)
	go func() {
		if err := util.RunResultServer(resultCh); err != nil {
			log.Fatalf("failed to start result server: %v", err)
		}
	}()

	log.Infof("creating `%d` random messages to be sent", globalFlags.NumberOfMessagesToSend)
	randomMessages, err := util.CreateRandomMessages(int(globalFlags.NumberOfMessagesToSend), 50)
	if err != nil {
		return fmt.Errorf("failed to create random messages: %v", err)
	}
	log.Debugf("created the following messages: `%+q`", randomMessages)

	log.Infof("discovering node ID for receiver node at `%s`", testNodeClients.ReceiverNodeClient.GetBaseUrl())
	nodeId, err := testNodeClients.ReceiverNodeClient.DiscoverOwnNodeId(globalFlags.AnalysisId)
	if err != nil {
		log.Fatalf("could not discover node ID for receiver node: %v", err)
	}

	log.Info("running send routine")
	var wgSender sync.WaitGroup
	wgSender.Add(1)
	runDedicatedMessageSendingRoutine(testNodeClients.SenderNodeClient, globalFlags.AnalysisId, nodeId, randomMessages, &wgSender)

	log.Info("running receive routine")
	receivedMessagesCh := make(chan []messagebroker.TestMessage, 1)
	var wgReceiver sync.WaitGroup
	wgReceiver.Add(1)
	runDedicatedMessageReceiveRoutine(resultCh, receivedMessagesCh, &wgReceiver)

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
	SortMessages(randomMessages)
	SortMessages(receivedMessages)

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

func getDedicatedMessagesTestNodeClients(bootstrapNodes []string, authClient messagebroker.MessageBrokerAuthClient) dedicatedMessagesTestNodeClients {
	return dedicatedMessagesTestNodeClients{
		SenderNodeClient:   messagebroker.NewMessageBrokerClient(bootstrapNodes[0], authClient.AcquireAccessToken),
		ReceiverNodeClient: messagebroker.NewMessageBrokerClient(bootstrapNodes[1], authClient.AcquireAccessToken),
	}
}

func runDedicatedMessageSendingRoutine(senderNodeClient messagebroker.MessageBrokerClient, analysisId string, targetNodeId messagebroker.NodeId, messages []messagebroker.TestMessage, wg *sync.WaitGroup) {
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

func runDedicatedMessageReceiveRoutine(resultCh <-chan messagebroker.TestMessage, receivedMessages chan<- []messagebroker.TestMessage, wg *sync.WaitGroup) {
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
