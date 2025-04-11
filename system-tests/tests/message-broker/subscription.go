package messagebroker

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
)

func (mbc *messageBrokerClient) CreateSubscription(analysisId string, webhookUrl string) (SubscriptionId, error) {
	subscriptionRequestBody := nodeCreateSubscriptionRequest{WebhookUrl: webhookUrl}

	subscriptionRequestBodyJson, err := json.Marshal(subscriptionRequestBody)
	if err != nil {
		return "", fmt.Errorf("failed to marshal subscription request body: %v", err)
	}

	req, err := http.NewRequest(http.MethodPost,
		fmt.Sprintf("%s/analyses/%s/messages/subscriptions", mbc.baseUrl, analysisId),
		bytes.NewReader(subscriptionRequestBodyJson),
	)
	if err != nil {
		return "", fmt.Errorf("failed to create subscription creation request: %v", err)
	}
	accessToken, err := mbc.accessTokenAcquirer()
	if err != nil {
		return "", fmt.Errorf("could not acquire access token for request: %v", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", accessToken))

	resp, err := mbc.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("failed to send subscription creation request to node at `%s`: %v", mbc.baseUrl, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusCreated {
		body, err := io.ReadAll(resp.Body)
		if err != nil {
			return "", fmt.Errorf("could not read response body after message broker returned non 201 code: %v", err)
		}
		return "", fmt.Errorf("message broker returned non 201 status code (%d): %s", resp.StatusCode, body)
	} else {
		body, err := io.ReadAll(resp.Body)
		if err != nil {
			return "", fmt.Errorf("could not read response body after message broker returned 201 code: %v", err)
		}

		var subscriptionResponse nodeSubscriptionResponse
		if err = json.Unmarshal(body, &subscriptionResponse); err != nil {
			return "", fmt.Errorf("could not unmarshal response body after message broker returned 201 code: %v", err)
		}
		return SubscriptionId(subscriptionResponse.SubscriptionId), nil
	}
}

func (mbc *messageBrokerClient) DeleteSubscription(analysisId string, subscriptionId SubscriptionId) error {
	req, err := http.NewRequest(http.MethodDelete,
		fmt.Sprintf("%s/analyses/%s/messages/subscriptions/%s", mbc.baseUrl, analysisId, string(subscriptionId)),
		nil,
	)
	if err != nil {
		return fmt.Errorf("failed to create subscription deletion request: %v", err)
	}
	accessToken, err := mbc.accessTokenAcquirer()
	if err != nil {
		return fmt.Errorf("could not acquire access token for request: %v", err)
	}

	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", accessToken))

	resp, err := mbc.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to send subscription deletion request to node at `%s`: %v", mbc.baseUrl, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusNoContent {
		body, err := io.ReadAll(resp.Body)
		if err != nil {
			return fmt.Errorf("could not read response body after message broker returned non 204 code: %v", err)
		}
		return fmt.Errorf("message broker returned non 204 status code (%d): %s", resp.StatusCode, body)
	}
	return nil
}
