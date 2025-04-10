package messagebroker

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
)

func (mbc *messageBrokerClient) DiscoverOwnNodeId(analysisId string) (NodeId, error) {
	req, err := http.NewRequest(http.MethodGet,
		fmt.Sprintf("%s/analyses/%s/participants/self", mbc.baseUrl, analysisId),
		nil,
	)
	if err != nil {
		return "", fmt.Errorf("failed to create self discovery request: %v", err)
	}
	accessToken, err := mbc.accessTokenAcquirer()
	if err != nil {
		return "", fmt.Errorf("could not acquire access token for request: %v", err)
	}
	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", accessToken))

	resp, err := mbc.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("failed to send self discovery request: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, err := io.ReadAll(resp.Body)
		if err != nil {
			return "", fmt.Errorf("could not read response body after message broker returned non 200 code: %v", err)
		}
		return "", fmt.Errorf("message broker returned non 200 status code (%d): %s", resp.StatusCode, body)
	} else {
		body, err := io.ReadAll(resp.Body)
		if err != nil {
			return "", fmt.Errorf("could not read response body after message broker returned 200 code: %v", err)
		}

		var participantResponse nodeParticipantResponse
		if err = json.Unmarshal(body, &participantResponse); err != nil {
			return "", fmt.Errorf("could not unmarshal response body after message broker returned 200 code: %v", err)
		}
		return NodeId(participantResponse.NodeId), nil
	}
}
