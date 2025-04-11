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
	sendBroadcastMessagesTestCmd = &cobra.Command{
		Use:   "send-broadcast-messages",
		Short: "Runs a system test where messages are sent to many recipients using broadcast",
		RunE:  sendBroadcastMessagesFunc,
	}
)

type TestNodeClients struct {
	SenderNodeClient    messagebroker.MessageBrokerClient
	ReceiverNodeAClient messagebroker.MessageBrokerClient
	ReceiverNodeBClient messagebroker.MessageBrokerClient
}

func sendBroadcastMessagesFunc(cmd *cobra.Command, _ []string) error {
	globalFlags := getGlobalFlags(cmd)

	log.SetFormatter(&log.TextFormatter{
		FullTimestamp: true,
	})
	log.Info("Running system test for sending messages to various recipients using broadcast")
	log.Infof("Running test case with analysis ID: `%s`", globalFlags.AnalysisId)
	log.Infof("Running test case with bootstrap nodes: `%s`", globalFlags.BootstrapNodes)

	if len(globalFlags.BootstrapNodes) < 3 {
		return fmt.Errorf("at least 3 bootstrap nodes have to be specified")
	}

	authClient := messagebroker.NewMessageBrokerAuthClient(globalFlags.NodeAuthBaseUrl, globalFlags.NodeAuthClientId, globalFlags.NodeAuthClientSecret)
	testNodeClients := getBroadcastMessagesTestNodeClients(globalFlags.BootstrapNodes, authClient)

	log.Infof("setting up subscription for result server at reciever node with base URL: `%s`", testNodeClients.ReceiverNodeAClient.GetBaseUrl())
	// TODO: remove hard coded value
	subscriptionIdA, err := configureReceiverNodeSubscription(testNodeClients.ReceiverNodeAClient, globalFlags.AnalysisId, "http://host.docker.internal:8080/results")
	if err != nil {
		return fmt.Errorf("could not configure receiver node")
	}
	defer func() {
		log.Info("cleaning up receiver node subscription")
		cleanUpReceiverNodeSubscription(testNodeClients.ReceiverNodeAClient, globalFlags.AnalysisId, subscriptionIdA)
	}()

	log.Infof("setting up subscription for result server at reciever node with base URL: `%s`", testNodeClients.ReceiverNodeBClient.GetBaseUrl())
	// TODO: remove hard coded value
	subscriptionIdB, err := configureReceiverNodeSubscription(testNodeClients.ReceiverNodeBClient, globalFlags.AnalysisId, "http://host.docker.internal:8080/results")
	if err != nil {
		return fmt.Errorf("could not configure receiver node")
	}
	defer func() {
		log.Info("cleaning up receiver node subscription")
		cleanUpReceiverNodeSubscription(testNodeClients.ReceiverNodeBClient, globalFlags.AnalysisId, subscriptionIdB)
	}()

	log.Info("Starting result server for receiving messages via subscriptions")
	resultCh := make(chan messagebroker.TestMessage, globalFlags.NumberOfMessagesToSend*2)
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

	log.Info("running send routine")
	var wgSender sync.WaitGroup
	wgSender.Add(1)
	runBroadcastMessageSendingRoutine(testNodeClients.SenderNodeClient, globalFlags.AnalysisId, randomMessages, &wgSender)

	log.Info("running receive routine")
	receivedMessagesCh := make(chan []messagebroker.TestMessage, 1)
	var wgReceiver sync.WaitGroup
	wgReceiver.Add(1)
	runBroadcastMessageReceiveRoutine(resultCh, receivedMessagesCh, &wgReceiver)

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

	if (2 * len(randomMessages)) != len(receivedMessages) {
		log.Fatalf("number of received messages (%d) is not twice the number of sent messages (%d)",
			len(receivedMessages), len(randomMessages))
	}

	for i := 0; i < len(randomMessages); i++ {
		if receivedMessages[2*i].Id != randomMessages[i].Id || receivedMessages[2*i+1].Id != randomMessages[i].Id {
			log.Warnf("received message 1: `%s`", receivedMessages[i].Id)
			log.Warnf("received message 2: `%s`", receivedMessages[i+1].Id)
			log.Warnf("sent message: `%s`", randomMessages[i].Id)
			log.Fatalf("sent message is not equal to the received messages")
		}
	}

	log.Info("Success - broadcasted and received messages are equal")

	return nil
}

func getBroadcastMessagesTestNodeClients(bootstrapNodes []string, authClient messagebroker.MessageBrokerAuthClient) TestNodeClients {
	return TestNodeClients{
		SenderNodeClient:    messagebroker.NewMessageBrokerClient(bootstrapNodes[0], authClient.AcquireAccessToken),
		ReceiverNodeAClient: messagebroker.NewMessageBrokerClient(bootstrapNodes[1], authClient.AcquireAccessToken),
		ReceiverNodeBClient: messagebroker.NewMessageBrokerClient(bootstrapNodes[2], authClient.AcquireAccessToken),
	}
}

func runBroadcastMessageSendingRoutine(senderNodeClient messagebroker.MessageBrokerClient, analysisId string, messages []messagebroker.TestMessage, wg *sync.WaitGroup) {
	go func() {
		defer wg.Done()

		for _, message := range messages {
			err := senderNodeClient.SendBroadcastMessage(analysisId, message)
			if err != nil {
				log.Errorf("could not send message: %v", err)
			}
		}
	}()
}

func runBroadcastMessageReceiveRoutine(resultCh <-chan messagebroker.TestMessage, receivedMessages chan<- []messagebroker.TestMessage, wg *sync.WaitGroup) {
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
