package util

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	messagebroker "mb-test-cli/message-broker"
)

func CreateRandomMessages(numberOfMessages int, messageLength uint8) ([]messagebroker.TestMessage, error) {
	messagesSet := map[string]string{}

	for {
		messageBytes := make([]byte, messageLength)
		if _, err := rand.Read(messageBytes); err != nil {
			return nil, fmt.Errorf("could not generate random message: %v", err)
		}

		message := hex.EncodeToString(messageBytes)

		_, ok := messagesSet[message]
		if !ok {
			messagesSet[message] = message
		}

		if len(messagesSet) == numberOfMessages {
			break
		}
	}

	messages := make([]messagebroker.TestMessage, len(messagesSet))
	i := 0
	for _, v := range messagesSet {
		messages[i] = messagebroker.TestMessage{Id: v}
		i++
	}

	return messages, nil
}
