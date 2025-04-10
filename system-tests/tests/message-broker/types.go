package messagebroker

type SubscriptionId string
type NodeId string

type TestMessage struct {
	Id string `json:"id"`
}

type nodeCreateSubscriptionRequest struct {
	WebhookUrl string `json:"webhookUrl"`
}

type nodeSubscriptionResponse struct {
	SubscriptionId string `json:"subscriptionId"`
	AnalysisId     string `json:"analysisId"`
	WebhookUrl     string `json:"webhookUrl"`
}

type nodeParticipantResponse struct {
	NodeId   string `json:"nodeId"`
	NodeType string `json:"nodeType"`
}

type nodeSendMessageRequest struct {
	Recipients []NodeId    `json:"recipients"`
	Message    TestMessage `json:"message"`
}

type keycloakTokenResponse struct {
	AccessToken string `json:"access_token"`
}
