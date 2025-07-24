package messagebroker

import (
	"fmt"
	"net/http"
)

func (mbc *messageBrokerClient) IsReady() (bool, error) {
	req, err := http.NewRequest(http.MethodGet,
		fmt.Sprintf("%s/actuator/health/readiness", mbc.managementBaseUrl),
		nil,
	)
	if err != nil {
		return false, fmt.Errorf("failed to create readiness check request: %v", err)
	}

	resp, err := mbc.httpClient.Do(req)
	if err != nil {
		return false, fmt.Errorf("failed to send readiness check request: %v", err)
	}
	defer resp.Body.Close()

	return resp.StatusCode == http.StatusOK, nil
}
