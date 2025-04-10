package cmd

import "github.com/spf13/cobra"

var (
	analysisId           string
	bootstrapNodes       []string
	nodeAuthBaseUrl      string
	nodeAuthClientId     string
	nodeAuthClientSecret string

	RootCmd = &cobra.Command{
		Use:   "mb-test-cli",
		Short: "A CLI application for running systems tests of the message broker.",
	}
)

func Execute() error {
	return RootCmd.Execute()
}

func init() {
	RootCmd.PersistentFlags().StringVar(&analysisId, "analysis-id", "", "unique identifier of the analysis to be used")
	RootCmd.PersistentFlags().StringSliceVar(&bootstrapNodes, "bootstrap-nodes", nil, "list of bootstrap nodes")
	RootCmd.PersistentFlags().StringVar(&nodeAuthBaseUrl, "node-auth-base-url", "", "base url for the auth service used for authentication")
	RootCmd.PersistentFlags().StringVar(&nodeAuthClientId, "node-auth-client-id", "", "client id used for authentication")
	RootCmd.PersistentFlags().StringVar(&nodeAuthClientSecret, "node-auth-client-secret", "", "client secret used for authentication")

	_ = RootCmd.MarkPersistentFlagRequired("analysis-id")
	_ = RootCmd.MarkPersistentFlagRequired("bootstrap-nodes")
	_ = RootCmd.MarkPersistentFlagRequired("node-auth-base-url")
	_ = RootCmd.MarkPersistentFlagRequired("node-auth-client-id")
	_ = RootCmd.MarkPersistentFlagRequired("node-auth-client-secret")

	RootCmd.AddCommand(sendDedicatedMessagesTestCmd)
}
