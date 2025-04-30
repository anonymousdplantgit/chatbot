package be.fgov.bosa.chatbot.chatbot.services;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;

public interface MinistryAssistant {
    /**
     * Process a user message and respond with RAG-enhanced information
     *
     * @param userMessage The user's message/query
     * @param memoryId Session identifier for conversation memory
     * @param systemPrompt System prompt with guardrails
     * @return Response containing the AI message
     */
    @SystemMessage("{{systemPrompt}}")
    Response<AiMessage> chat(
            @UserMessage String userMessage,
            @MemoryId String memoryId,
            @V("systemPrompt") String systemPrompt);

    /**
     * Stream a response for real-time UI updates
     *
     * @param userMessage The user's message/query
     * @param memoryId Session identifier for conversation memory
     * @param systemPrompt System prompt with guardrails
     * @return Streaming response of token chunks
     */
    @SystemMessage("{{systemPrompt}}")
    Flux<String> stream(
            @UserMessage String userMessage,
            @MemoryId String memoryId,
            @V("systemPrompt") String systemPrompt);

    /**
     * Process a user message with metadata for more context
     *
     * @param userMessage The user's message/query
     * @param memoryId Session identifier for conversation memory
     * @param systemPrompt System prompt with guardrails
     * @param metadata Additional context metadata
     * @return Response containing the AI message
     */
    @SystemMessage("{{systemPrompt}}")
    Response<AiMessage> chatWithMetadata(
            @UserMessage String userMessage,
            @MemoryId String memoryId,
            @V("systemPrompt") String systemPrompt,
            @V("metadata") String metadata);

    /**
     * Create a self-critique of a potential response to check for hallucination
     *
     * @param draftResponse Draft response to evaluate
     * @param retrievedContext The retrieved context used
     * @return Evaluation score and commentary on response quality
     */
    @SystemMessage("You are a critical evaluator. Your job is to evaluate if a draft response " +
            "is properly grounded in the retrieved context. Score from 0-10 where 0 means " +
            "completely made up and 10 means perfectly supported by context.")
    Response<AiMessage> evaluateResponseGrounding(
            @UserMessage("USER QUERY: {{userQuery}}\n\n" +
                    "RETRIEVED CONTEXT: {{retrievedContext}}\n\n" +
                    "DRAFT RESPONSE: {{draftResponse}}\n\n" +
                    "Evaluate if the draft response is properly grounded in the retrieved context. " +
                    "Score from 0-10 and explain your reasoning. Highlight any statements that " +
                    "aren't supported by the context.")
            String evaluationPrompt,
            @V("userQuery") String userQuery,
            @V("retrievedContext") String retrievedContext,
            @V("draftResponse") String draftResponse);
}