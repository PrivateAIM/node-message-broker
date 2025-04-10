package cmd

import "github.com/spf13/cobra"

type GlobalFlags struct {
	AnalysisId             string
	BootstrapNodes         []string
	NodeAuthBaseUrl        string
	NodeAuthClientId       string
	NodeAuthClientSecret   string
	NumberOfMessagesToSend uint16
}

func getGlobalFlags(cmd *cobra.Command) *GlobalFlags {
	analysisId, _ := cmd.Flags().GetString("analysis-id")
	BootstrapNodes, _ := cmd.Flags().GetStringSlice("bootstrap-nodes")
	nodeAuthBaseUrl, _ := cmd.Flags().GetString("node-auth-base-url")
	nodeAuthClientId, _ := cmd.Flags().GetString("node-auth-client-id")
	nodeAuthClientSecret, _ := cmd.Flags().GetString("node-auth-client-secret")
	numberOfMessagesToSend, _ := cmd.Flags().GetUint16("number-of-messages-to-send")

	return &GlobalFlags{
		AnalysisId:             analysisId,
		BootstrapNodes:         BootstrapNodes,
		NodeAuthBaseUrl:        nodeAuthBaseUrl,
		NodeAuthClientId:       nodeAuthClientId,
		NodeAuthClientSecret:   nodeAuthClientSecret,
		NumberOfMessagesToSend: numberOfMessagesToSend,
	}
}
