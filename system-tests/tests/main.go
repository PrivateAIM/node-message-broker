package main

import (
	"mb-test-cli/cmd"

	log "github.com/sirupsen/logrus"
)

func main() {
	if err := cmd.RootCmd.Execute(); err != nil {
		log.Fatalf("error executing test case: %v", err)
	}
}
