package be.fgov.bosa.chatbot.chatbot.services;

import be.fgov.bosa.chatbot.chatbot.config.AppPropertiesConfig;
import be.fgov.bosa.chatbot.chatbot.requests.ChatRequest;
import be.fgov.bosa.chatbot.chatbot.resources.ChatResponse;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@AllArgsConstructor
@Getter
@Setter
@Service
@Slf4j
public class ChatBotService {

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EnhancedColangLoaderService enhancedColangLoader;
    private final AppPropertiesConfig appConfigProperties;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ResponseVerifier responseVerifier;

    // In-memory chat session storage
    private final Map<String, ChatMemory> chatMemories = new ConcurrentHashMap<>();

    /**
     * Main chat method - provides RAG-enhanced responses with strict guardrails
     */
    public ChatResponse chat(ChatRequest request) throws IOException {
        try {
            // Initialize or retrieve session
            String sessionId = initializeSession(request);
            ChatMemory chatMemory = getOrCreateChatMemory(sessionId);

            // Get relevant knowledge based on user query
            RetrievedKnowledge retrievedKnowledge = retrieveRelevantKnowledge(request.getMessage(),request.getBotId());

            // Generate response with guardrails
            AIResponseData responseData = generateAIResponse(request.getMessage(),
                    retrievedKnowledge,
                    chatMemory,
                    sessionId);

            // Verify response against knowledge base to prevent hallucination
            if (appConfigProperties.getChatbot().isVerifyResponses()) {
                responseData = verifyAndCorrectResponse(
                        request.getMessage(),
                        responseData,
                        retrievedKnowledge.getContext()
                );
            }

            // Build and return the chat response
            return buildChatResponse(sessionId, request.getMessage(),
                    responseData,
                    retrievedKnowledge.getRetrievalConfidence());
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            throw e;
        }
    }

    /**
     * Initialize or validate the session ID
     */
    private String initializeSession(ChatRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = UUID.randomUUID().toString();
        }
        return conversationId;
    }

    /**
     * Get or create chat memory for this session
     */
    private ChatMemory getOrCreateChatMemory(String sessionId) {
        return chatMemories.computeIfAbsent(sessionId,
                id -> MessageWindowChatMemory.builder()
                        .maxMessages(appConfigProperties.getChatbot().getMemoryTokenLimit() / 4) // Conservative estimate
                        .build());
    }

    /**
     * Retrieve relevant knowledge from the vector database
     */
    private RetrievedKnowledge retrieveRelevantKnowledge(String userQuery, String botId) {

        // Search for relevant content with metadata filter to ensure botId match
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(userQuery).content())
                        .filter( metadataKey("botId").isEqualTo(botId))
                        .maxResults(5) // Limit results to prevent noise
                        .minScore(0.7) // Increased minimum relevance threshold
                        .build());

        // Log retrieved segments for debugging
        logRetrievedSegments(searchResult);

        // Build context from retrieved knowledge
        String context = buildContextFromSearchResults(searchResult);

        // Calculate confidence from retrieval results
        double retrievalConfidence = calculateRetrievalConfidence(searchResult);

        return new RetrievedKnowledge(context, retrievalConfidence, searchResult);
    }

    /**
     * Log retrieved segments for debugging
     */
    private void logRetrievedSegments(EmbeddingSearchResult<TextSegment> searchResult) {
        log.info("Retrieved {} relevant segments", searchResult.matches().size());
        for (EmbeddingMatch<TextSegment> match : searchResult.matches()) {
            log.info("############################\nSegment score: {} - Content: {}",
                    match.score(),
                    match.embedded().text().substring(0, Math.min(100, match.embedded().text().length())) + "...");
        }
    }

    /**
     * Build context string from search results
     */
    // Update in ChatBotService.java - replace buildContextFromSearchResults method
    private String buildContextFromSearchResults(EmbeddingSearchResult<TextSegment> searchResult) {
        StringBuilder contextBuilder = new StringBuilder();

        // First, sort matches by relevance score (highest first)
        List<EmbeddingMatch<TextSegment>> sortedMatches = searchResult.matches().stream()
                .sorted(Comparator.comparing(EmbeddingMatch::score, Comparator.reverseOrder()))
                .toList();

        int segmentCounter = 1;
        // Track seen text to avoid duplicates
        Set<String> seenText = new HashSet<>();

        for (EmbeddingMatch<TextSegment> match : sortedMatches) {
            String text = match.embedded().text().trim();

            // Skip duplicate or highly similar content
            if (!seenText.add(text)) {
                continue;
            }

            // Add metadata if available
            Metadata metadata = match.embedded().metadata();
            String source = metadata != null && metadata.containsKey("source")
                    ? metadata.getString("source")
                    : "Unknown source";

            contextBuilder.append("Segment ").append(segmentCounter++)
                    .append(" [Source: ").append(source).append(", Relevance: ")
                    .append(String.format("%.2f", match.score())).append("]: ")
                    .append(text)
                    .append("\n\n");
        }

        return contextBuilder.toString();
    }

    /**
     * Calculate confidence based on retrieval results
     *
     * This method evaluates how good the vector search results are by considering:
     * 1. Relevance scores of matched segments
     * 2. Number and distribution of results
     * 3. Score variance and threshold performance
     *
     * @param searchResult The search results from the embedding store
     * @return Confidence score between 0.0 and 1.0
     */
    private double calculateRetrievalConfidence(EmbeddingSearchResult<TextSegment> searchResult) {
        // Handle empty results
        if (searchResult == null || searchResult.matches().isEmpty()) {
            log.debug("No search results found, returning minimum confidence");
            return 0.1; // Very low confidence if no results
        }

        // Extract match scores from search results
        List<Double> scores = searchResult.matches().stream()
                .map(EmbeddingMatch::score)
                .collect(Collectors.toList());

        // Get number of matches and statistics on scores
        int matchCount = scores.size();
        double topScore = scores.isEmpty() ? 0 : scores.get(0); // Assuming sorted results
        double avgScore = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        log.debug("Retrieval stats: matches={}, topScore={}, avgScore={}",
                matchCount, topScore, avgScore);

        // Calculate component confidence factors

        // 1. Top Result Quality (30%)
        // How good is our best match? Scale from 0.7 (minimum threshold) to 1.0 (perfect match)
        double topScoreConfidence = normalizeScore(topScore, 0.7, 1.0);

        // 2. Average Score Quality (25%)
        // How good are all our matches on average?
        double avgScoreConfidence = normalizeScore(avgScore, 0.7, 1.0);

        // 3. Result Count Factor (20%)
        // More results up to optimal count (5) increases confidence
        double optimalResultCount = 5.0;
        double resultCountConfidence = Math.min(1.0, matchCount / optimalResultCount);

        // 4. Score Distribution (15%)
        // Are scores tightly clustered (good) or highly variable (concerning)?
        double scoreVariance = calculateVariance(scores);
        double distributionConfidence = Math.max(0.0, 1.0 - (scoreVariance * 2)); // Lower variance is better

        // 5. Threshold Satisfaction (10%)
        // What percentage of results meet high confidence threshold (0.8)?
        double highConfThreshold = 0.8;
        long resultsMeetingHighThreshold = scores.stream()
                .filter(score -> score >= highConfThreshold)
                .count();
        double thresholdConfidence = resultsMeetingHighThreshold / (double) Math.max(1, matchCount);

        // Combine factors with weights
        double retrievalConfidence =
                (topScoreConfidence * 0.30) +
                        (avgScoreConfidence * 0.25) +
                        (resultCountConfidence * 0.20) +
                        (distributionConfidence * 0.15) +
                        (thresholdConfidence * 0.10);

        log.debug("Retrieval confidence components: topScore={}, avgScore={}, resultCount={}, " +
                        "distribution={}, thresholdSatisfaction={}, final={}",
                topScoreConfidence, avgScoreConfidence, resultCountConfidence,
                distributionConfidence, thresholdConfidence, retrievalConfidence);

        // Ensure result is in valid range
        return Math.min(1.0, Math.max(0.0, retrievalConfidence));
    }

    /**
     * Normalize a score to a range between 0 and 1
     *
     * @param score Raw score to normalize
     * @param min Minimum threshold (scores below this will be 0)
     * @param max Maximum value (scores above this will be 1)
     * @return Normalized score between 0 and 1
     */
    private double normalizeScore(double score, double min, double max) {
        if (score <= min) return 0.0;
        if (score >= max) return 1.0;
        return (score - min) / (max - min);
    }

    /**
     * Calculate variance of a list of scores
     *
     * @param scores List of score values
     * @return Variance value
     */
    private double calculateVariance(List<Double> scores) {
        if (scores.isEmpty()) return 0.0;

        // Calculate mean
        double mean = scores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // Calculate sum of squared differences from mean
        double sumSquaredDiffs = scores.stream()
                .mapToDouble(score -> Math.pow(score - mean, 2))
                .sum();

        // Return variance
        return sumSquaredDiffs / scores.size();
    }

    /**
     * Calculate standard deviation from a variance value
     *
     * @param variance Variance value
     * @return Standard deviation
     */
    private double calculateStdDev(double variance) {
        return Math.sqrt(variance);
    }

    /**
     * Generate AI response with strong guardrails
     */
    private AIResponseData generateAIResponse(String userQuery,
                                              RetrievedKnowledge knowledge,
                                              ChatMemory chatMemory,
                                              String sessionId) throws IOException {
        // Create augmented prompt with retrieved knowledge
        String augmentedPrompt = createAugmentedPrompt(userQuery, knowledge.getContext());

        log.info("#########################\naugmentedPrompt: {}", augmentedPrompt);

        // Get system prompt with guardrails
        String systemPrompt = enhancedColangLoader.createFormattedSystemPrompt();

        log.info("#########################\nsystemPrompt: {}", systemPrompt);

        // Create the assistant interface
        MinistryAssistant assistant = AiServices.builder(MinistryAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(10))
                .systemMessageProvider(id -> systemPrompt)
                .build();

        // Get response
        Response<AiMessage> aiResponse = assistant.chat(augmentedPrompt, sessionId, systemPrompt);

        // Extract text response
        String responseText = aiResponse.content().text();

        // Calculate response confidence
        double confidenceScore = calculateConfidence(responseText, userQuery, knowledge);

        return new AIResponseData(responseText, confidenceScore);
    }

    /**
     * Verify response against knowledge base and correct if needed
     */
    private AIResponseData verifyAndCorrectResponse(String userQuery,
                                                    AIResponseData responseData,
                                                    String context) {
        // Verify if response is grounded in the knowledge base
        ResponseVerifier.VerificationResult result =
                responseVerifier.verifyResponseGrounding(userQuery, responseData.getResponseText(), context);

        log.info("Response verification result: score={}, verified={}",
                result.getScore(), result.isVerified());

        if (!result.isVerified()) {
            log.warn("Response failed verification (score: {}). Ungrounded claims: {}",
                    result.getScore(), result.getUngroundedClaims());

            // Regenerate response with more explicit instructions
            try {
                // Create the assistant interface with stricter guardrails
                MinistryAssistant assistant = AiServices.builder(MinistryAssistant.class)
                        .chatLanguageModel(chatLanguageModel)
                        .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                        .systemMessageProvider(chatMemoryId -> {
                            try {
                                return enhancedColangLoader.createFormattedSystemPrompt();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .build();

                // Create a more constrained prompt
                String strictPrompt = "IMPORTANT: Your previous response contained information not " +
                        "supported by the context. Please answer using ONLY facts from the provided context.\n\n" +
                        "USER QUESTION: " + userQuery + "\n\n" +
                        "CONTEXT (only use information from here):\n" + context + "\n\n" +
                        "If the context doesn't contain enough information to answer the question, " +
                        "clearly state that you don't have enough information.";

                // Get corrected response
                Response<AiMessage> correctedResponse = assistant.chat(
                        strictPrompt,
                        UUID.randomUUID().toString(), // Use new session to avoid contamination
                        enhancedColangLoader.createFormattedSystemPrompt()
                );

                // Use the corrected response with reduced confidence
                return new AIResponseData(
                        correctedResponse.content().text(),
                        result.getScore() // Cap confidence
                );
            } catch (Exception e) {
                log.error("Error regenerating response after verification failure", e);
                // If regeneration fails, add a disclaimer to the original response
                String disclaimerResponse = responseData.getResponseText() +
                        "\n\nPlease note: Some parts of this response may not be directly supported " +
                        "by our ministry's knowledge base. For verified information, please contact " +
                        "the ministry directly.";
                return new AIResponseData(disclaimerResponse, responseData.getConfidenceScore() * 0.7);
            }
        }


        return new AIResponseData(responseData.getResponseText(), result.getScore());
    }

    /**
     * Create augmented prompt incorporating retrieved knowledge
     */
    private String createAugmentedPrompt(String userQuery, String context) {
        StringBuilder promptBuilder = new StringBuilder();

        // Simple instruction to respond in the same language as the user's query
        promptBuilder.append("IMPORTANT: Always respond in the same language that the user used to ask the question.\n\n");

        // Clear instruction to use only provided context
        promptBuilder.append("Answer using ONLY the information provided below. ")
                .append("If the provided information isn't sufficient to answer the question, ")
                .append("say you don't have enough information rather than making up an answer.\n\n");

        // User question
        promptBuilder.append("USER QUESTION: ").append(userQuery).append("\n\n");

        // Context from knowledge base
        promptBuilder.append("RELEVANT INFORMATION FROM KNOWLEDGE BASE:\n")
                .append(context).append("\n");

        // Final instruction
        promptBuilder.append("Based ONLY on the above information, provide a concise, accurate response. ")
                .append("Do not add any information that isn't in the provided context. ")
                .append("If you're unsure, express your uncertainty clearly.");

        return promptBuilder.toString();
    }

    /**
     * Calculate confidence score for the response
     */
    private double calculateConfidence(String response, String question, RetrievedKnowledge knowledge) {
        // Base confidence factors
        double retrievalQualityWeight = 0.4;
        double contentVerificationWeight = 0.4;
        double semanticSimilarityWeight = 0.2;

        // 1. Retrieval quality component (40%)
        double retrievalScore = calculateRetrievalConfidence(knowledge.getSearchResult());

        // 2. Content verification component (40%)
        double contentVerificationScore = calculateContentVerificationScore(response, knowledge.getContext());

        // 3. Semantic similarity between question and response (20%)
        double semanticSimilarityScore = calculateSemanticSimilarity(question, response);

        // Weighted average for final confidence score
        double confidenceScore = (retrievalScore * retrievalQualityWeight) +
                (contentVerificationScore * contentVerificationWeight) +
                (semanticSimilarityScore * semanticSimilarityWeight);

        // Ensure score is within 0.0-1.0 range
        return Math.min(1.0, Math.max(0.0, confidenceScore));
    }
    /**
     * Calculate content verification score
     * @param response The generated response
     * @param context The context used for generation
     * @return Verification score between 0.0 and 1.0
     */
    private double calculateContentVerificationScore(String response, String context) {
        // Short responses to very long context may indicate cherry picking or limited relevance
        double contextToResponseRatio = Math.min(1.0, (double) response.length() / Math.max(1, context.length()) * 10);

        // Check if response contains uncertain language patterns
        double uncertaintyPenalty = containsUncertaintyMarkers(response) ? 0.3 : 0.0;

        // Check if key terms from context appear in response
        double termOverlapScore = calculateTermOverlap(context, response);

        return (contextToResponseRatio * 0.3) + (termOverlapScore * 0.7) - uncertaintyPenalty;
    }

    /**
     * Calculate term overlap between context and response
     */
    private double calculateTermOverlap(String context, String response) {
        // Extract significant terms (nouns, proper nouns, etc.) from context
        Set<String> contextTerms = extractSignificantTerms(context);
        Set<String> responseTerms = extractSignificantTerms(response);

        if (contextTerms.isEmpty()) {
            return 0.5; // Neutral score if no significant terms found
        }

        // Calculate intersection of terms
        Set<String> commonTerms = new HashSet<>(contextTerms);
        commonTerms.retainAll(responseTerms);

        // Normalize by context terms count
        return (double) commonTerms.size() / contextTerms.size();
    }

    /**
     * Extract significant terms from text
     * This is a simplified implementation focusing on potential key terms
     */
    private Set<String> extractSignificantTerms(String text) {
        Set<String> terms = new HashSet<>();

        // Simple heuristic: extract capitalized words and remove stopwords
        String[] words = text.split("\\s+");
        for (String word : words) {
            // Clean word and check if it's significant
            String cleaned = word.replaceAll("[^a-zA-Z0-9]", "").trim();
            if (cleaned.length() > 3 && !isStopWord(cleaned)) {
                terms.add(cleaned.toLowerCase());
            }
        }

        return terms;
    }

    /**
     * Simple stopword check
     */
    private boolean isStopWord(String word) {
        Set<String> stopwords = Set.of("the", "and", "for", "that", "with", "this", "from", "your", "have",
                "are", "not", "been", "when", "where", "what", "which", "there", "they",
                "would", "could", "should", "about");
        return stopwords.contains(word.toLowerCase());
    }
    /**
     * Check if response contains uncertainty markers
     */
    private boolean containsUncertaintyMarkers(String text) {
        String[] uncertaintyPatterns = {
                "I'm not sure", "I am not sure", "I'm uncertain", "may be", "might be",
                "could be", "possibly", "perhaps", "not enough information",
                "can't determine", "cannot determine", "not clear", "unclear"
        };

        String lowercaseText = text.toLowerCase();
        for (String pattern : uncertaintyPatterns) {
            if (lowercaseText.contains(pattern.toLowerCase())) {
                return true;
            }
        }

        return false;
    }
    /**
     * Calculate semantic similarity between question and response
     * @param question The user question
     * @param response The generated response
     * @return Similarity score between 0.0 and 1.0
     */
    private double calculateSemanticSimilarity(String question, String response) {
        try {
            // Use embedding model to calculate semantic similarity
            float[] questionEmbedding = embeddingModel.embed(question).content().vector();
            float[] responseEmbedding = embeddingModel.embed(response).content().vector();

            // Calculate cosine similarity
            return calculateCosineSimilarity(questionEmbedding, responseEmbedding);
        } catch (Exception e) {
            log.warn("Error calculating semantic similarity: {}", e.getMessage());
            return 0.5; // Neutral score on error
        }
    }
    /**
     * Calculate cosine similarity between two embedding vectors
     */
    private double calculateCosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            normA += vec1[i] * vec1[i];
            normB += vec2[i] * vec2[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    /**
     * Build the final chat response
     */
    private ChatResponse buildChatResponse(String sessionId,
                                           String userQuery,
                                           AIResponseData responseData,
                                           double retrievalConfidence) {
        // Build response object
        ChatResponse chatResponse = ChatResponse.builder()
                .sessionId(sessionId)
                .response(responseData.getResponseText())
                .confidence(responseData.getConfidenceScore())
                .retrievalConfidence(retrievalConfidence)
                .build();

        // Add low confidence warning if needed
        if (responseData.getConfidenceScore() < appConfigProperties.getChatbot().getConfidenceThreshold()) {
            chatResponse.setLowConfidence(true);
            chatResponse.setFallbackMessage("I'm not entirely confident in this answer. " +
                    "The information in our knowledge base may be limited on this topic. " +
                    "Please consider contacting the ministry directly for verified information.");
        }

        // Log the interaction
        logInteraction(sessionId, userQuery, responseData.getResponseText(),
                responseData.getConfidenceScore(), retrievalConfidence);

        return chatResponse;
    }

    /**
     * Log interactions for audit and improvement
     */
    private void logInteraction(String sessionId, String question, String answer,
                                double confidence, double retrievalConfidence) {
        log.info("Interaction - SessionID: {}, Question: {}, Answer length: {}, " +
                        "Response confidence: {}, Retrieval confidence: {}",
                sessionId, question, answer.length(), confidence, retrievalConfidence);
    }

    /**
     * Helper class to store retrieved knowledge data
     */
    private static class RetrievedKnowledge {
        private final String context;
        private final double retrievalConfidence;
        private final EmbeddingSearchResult<TextSegment> searchResult;

        public RetrievedKnowledge(String context, double retrievalConfidence,
                                  EmbeddingSearchResult<TextSegment> searchResult) {
            this.context = context;
            this.retrievalConfidence = retrievalConfidence;
            this.searchResult = searchResult;
        }

        public String getContext() {
            return context;
        }

        public double getRetrievalConfidence() {
            return retrievalConfidence;
        }

        public EmbeddingSearchResult<TextSegment> getSearchResult() {
            return searchResult;
        }
    }

    /**
     * Helper class to store AI response data
     */
    private static class AIResponseData {
        private final String responseText;
        private final double confidenceScore;

        public AIResponseData(String responseText, double confidenceScore) {
            this.responseText = responseText;
            this.confidenceScore = confidenceScore;
        }

        public String getResponseText() {
            return responseText;
        }

        public double getConfidenceScore() {
            return confidenceScore;
        }
    }
}