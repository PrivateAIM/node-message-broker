package messagebroker

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
)

func (mbc *messageBrokerClient) SendMessage(analysisId string, recipientId NodeId, message TestMessage) error {
	messageRequestBody := nodeSendMessageRequest{
		Recipients: []NodeId{recipientId},
		Message:    message,
	}

	payloadBuf := new(bytes.Buffer)
	json.NewEncoder(payloadBuf).Encode(messageRequestBody)
	req, err := http.NewRequest(
		http.MethodPost,
		fmt.Sprintf("%s/analyses/%s/messages", mbc.baseUrl, analysisId),
		payloadBuf,
	)
	if err != nil {
		return fmt.Errorf("could not create request to send message to node at `%s`: %v", mbc.baseUrl, err)
	}
	accessToken, err := mbc.accessTokenAcquirer()
	if err != nil {
		return fmt.Errorf("could not acquire access token for request: %v", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", accessToken))
	resp, err := mbc.httpClient.Do(req)

	if err != nil {
		return fmt.Errorf("could not send message to node at `%s`: %v", mbc.baseUrl, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusAccepted {
		body, err := io.ReadAll(resp.Body)
		if err != nil {
			return fmt.Errorf("could not read response body after message broker returned non 202 code: %v", err)
		}
		return fmt.Errorf("message broker returned non 202 status code (%d): %s", resp.StatusCode, body)
	}
	return nil
}

func (mbc *messageBrokerClient) SendBroadcastMessage(analysisId string, message TestMessage) error {
	messageRequestBody := nodeSendBroadcastMessageRequest{
		Message: message,
	}

	payloadBuf := new(bytes.Buffer)
	json.NewEncoder(payloadBuf).Encode(messageRequestBody)
	req, err := http.NewRequest(
		http.MethodPost,
		fmt.Sprintf("%s/analyses/%s/messages/broadcast", mbc.baseUrl, analysisId),
		payloadBuf,
	)
	if err != nil {
		return fmt.Errorf("could not create request to send broadcast message to node at `%s`: %v", mbc.baseUrl, err)
	}
	accessToken, err := mbc.accessTokenAcquirer()
	if err != nil {
		return fmt.Errorf("could not acquire access token for request: %v", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", accessToken))
	resp, err := mbc.httpClient.Do(req)

	if err != nil {
		return fmt.Errorf("could not send broadcast message to node at `%s`: %v", mbc.baseUrl, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusAccepted {
		body, err := io.ReadAll(resp.Body)
		if err != nil {
			return fmt.Errorf("could not read response body after message broker returned non 202 code: %v", err)
		}
		return fmt.Errorf("message broker returned non 202 status code (%d): %s", resp.StatusCode, body)
	}
	return nil
}
