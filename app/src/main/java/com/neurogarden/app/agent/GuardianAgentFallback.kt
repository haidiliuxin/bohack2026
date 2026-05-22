package com.neurogarden.app.agent

class GuardianAgentFallback(
    private val primary: GuardianAgentApi,
    private val fallback: GuardianAgentApi = MockGuardianAgentApi()
) : GuardianAgentApi {
    override suspend fun analyzeSignals(request: AgentSignalRequest): AgentSignalResponse =
        runCatching { primary.analyzeSignals(request) }.getOrElse { fallback.analyzeSignals(request) }

    override suspend fun tuneThresholds(request: ThresholdTuningRequest): ThresholdTuningResponse =
        runCatching { primary.tuneThresholds(request) }.getOrElse { fallback.tuneThresholds(request) }

    override suspend fun generateCareMessage(request: CareMessageRequest): CareMessageResponse =
        runCatching { primary.generateCareMessage(request) }.getOrElse { fallback.generateCareMessage(request) }

    override suspend fun continueSupportConversation(request: SupportConversationRequest): SupportConversationResponse =
        runCatching { primary.continueSupportConversation(request) }.getOrElse { fallback.continueSupportConversation(request) }
}
