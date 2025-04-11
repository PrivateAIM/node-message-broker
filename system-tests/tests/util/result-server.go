package util

import (
	"encoding/json"
	"fmt"
	"io"
	messagebroker "mb-test-cli/message-broker"
	"net/http"

	"github.com/go-chi/chi/v5"
	log "github.com/sirupsen/logrus"
)

const resultEndpointPath string = "/results"
const resultServerPort uint16 = 8080

func setupResultServerRouter(messageCh chan<- messagebroker.TestMessage) chi.Router {
	router := chi.NewRouter()
	router.Post(resultEndpointPath, func(w http.ResponseWriter, r *http.Request) {
		defer r.Body.Close()

		requestBody, err := io.ReadAll(r.Body)
		if err != nil {
			log.Errorf("failed to read request body: %v", err)
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		log.Debugf("received message: `%s`", requestBody)

		var parsedMessage messagebroker.TestMessage
		if err := json.Unmarshal(requestBody, &parsedMessage); err != nil {
			log.Errorf("could not parse received result: %v", err)
			return
		}

		messageCh <- parsedMessage

		if _, err := w.Write([]byte{}); err != nil {
			log.Errorf("failed to send response: %v", err)
		}
	})

	return router
}

func RunResultServer(messageCh chan<- messagebroker.TestMessage) error {
	router := setupResultServerRouter(messageCh)
	log.Infof("result server is running on port `%d`", resultServerPort)
	return http.ListenAndServe(fmt.Sprintf(":%d", resultServerPort), router)
}

func ResultServerResultEndpoint() string {
	return fmt.Sprintf("http://host.docker.internal:%d%s", resultServerPort, resultEndpointPath)
}
