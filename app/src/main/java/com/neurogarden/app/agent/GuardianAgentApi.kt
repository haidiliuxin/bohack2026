package com.neurogarden.app.agent

interface GuardianAgentApi {
    suspend fun analyzeSignals(request: AgentSignalRequest): AgentSignalResponse
    suspend fun tuneThresholds(request: ThresholdTuningRequest): ThresholdTuningResponse
    suspend fun generateCareMessage(request: CareMessageRequest): CareMessageResponse
    suspend fun continueSupportConversation(request: SupportConversationRequest): SupportConversationResponse
}
