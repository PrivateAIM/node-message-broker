package messagebroker

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type MessageBrokerAuthClient interface {
	AcquireAccessToken() (string, error)
}

type messageBrokerAuthClient struct {
	baseUrl      string
	httpClient   http.Client
	clientId     string
	clientSecret string
}

func NewMessageBrokerAuthClient(baseUrl string, clientId string, clientSecret string) MessageBrokerAuthClient {
	return &messageBrokerAuthClient{
		baseUrl:      baseUrl,
		httpClient:   http.Client{Timeout: time.Duration(1) * time.Second},
		clientId:     clientId,
		clientSecret: clientSecret,
	}
}

func (mbac *messageBrokerAuthClient) AcquireAccessToken() (string, error) {
	data := url.Values{}
	data.Set("grant_type", "client_credentials")
	data.Set("client_id", mbac.clientId)
	data.Set("client_secret", mbac.clientSecret)

	resp, err := mbac.httpClient.Post(
		fmt.Sprintf("%s/realms/privateaim/protocol/openid-connect/token", mbac.baseUrl),
		"application/x-www-form-urlencoded",
		strings.NewReader(data.Encode()),
	)

	if err != nil {
		return "", fmt.Errorf("could not acquire access token: %v", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("could not read response body: %v", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("auth service responded with status code `%d`: %s", resp.StatusCode, body)
	} else {
		var keycloakResponse keycloakTokenResponse
		if err = json.Unmarshal(body, &keycloakResponse); err != nil {
			return "", fmt.Errorf("could not read response from Keycloak: %v", err)
		}

		return keycloakResponse.AccessToken, nil
	}
}
