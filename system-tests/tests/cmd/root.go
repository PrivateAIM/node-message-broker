package cmd

import (
	"github.com/spf13/cobra"
)

var (
	AnalysisId               string
	BootstrapNodes           []string
	BootstrapManagementNodes []string
	NodeAuthBaseUrl          string
	NodeAuthClientId         string
	NodeAuthClientSecret     string
	NumberOfMessagesToSend   uint16

	globalFlags = GlobalFlags{}
	RootCmd     = &cobra.Command{
		Use:   "mb-test-cli",
		Short: "A CLI application for running system tests of the message broker.",
	}
)

func Execute() error {
	return RootCmd.Execute()
}

func init() {
	RootCmd.PersistentFlags().StringVar(&globalFlags.AnalysisId, "analysis-id", "", "unique identifier of the analysis to be used")
	RootCmd.PersistentFlags().StringSliceVar(&globalFlags.BootstrapNodes, "bootstrap-nodes", nil, "list of bootstrap nodes")
	RootCmd.PersistentFlags().StringSliceVar(&globalFlags.BootstrapManagementNodes, "bootstrap-management-nodes", nil, "list of management nodes for the specified bootstrap nodes (equal to the management server of a node)")
	RootCmd.PersistentFlags().StringVar(&globalFlags.NodeAuthBaseUrl, "node-auth-base-url", "", "base url for the auth service used for authentication")
	RootCmd.PersistentFlags().StringVar(&globalFlags.NodeAuthClientId, "node-auth-client-id", "", "client id used for authentication")
	RootCmd.PersistentFlags().StringVar(&globalFlags.NodeAuthClientSecret, "node-auth-client-secret", "", "client secret used for authentication")
	RootCmd.PersistentFlags().Uint16Var(&globalFlags.NumberOfMessagesToSend, "number-of-messages-to-send", 10, "number of messages to be send during a test case")

	_ = RootCmd.MarkPersistentFlagRequired("analysis-id")
	_ = RootCmd.MarkPersistentFlagRequired("bootstrap-nodes")
	_ = RootCmd.MarkPersistentFlagRequired("bootstrap-management-nodes")
	_ = RootCmd.MarkPersistentFlagRequired("node-auth-base-url")
	_ = RootCmd.MarkPersistentFlagRequired("node-auth-client-id")
	_ = RootCmd.MarkPersistentFlagRequired("node-auth-client-secret")

	RootCmd.AddCommand(sendDedicatedMessagesTestCmd)
	RootCmd.AddCommand(sendBroadcastMessagesTestCmd)
}
