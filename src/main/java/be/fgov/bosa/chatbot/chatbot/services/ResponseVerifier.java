package be.fgov.bosa.chatbot.chatbot.services;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for verifying AI responses against knowledge base content
 * to prevent hallucination and ensure factual accuracy
 */
@Slf4j
@Component
@AllArgsConstructor
public class ResponseVerifier {

    private final ChatLanguageModel chatLanguageModel;

    /**
     * Verify if a response is properly grounded in the retrieved context
     *
     * @param userQuery Original user query
     * @param draftResponse The generated response to verify
     * @param retrievedContext The context used to generate the response
     * @return VerificationResult with score and assessment
     */
    public VerificationResult verifyResponseGrounding(
            String userQuery,
            String draftResponse,
            String retrievedContext) {

        try {
            // Create verification prompt
            String verificationPrompt = createVerificationPrompt(userQuery, retrievedContext, draftResponse);

            // Send the verification prompt to the LLM using chat method

            // Extract evaluation text
            String evaluation = chatLanguageModel.chat(verificationPrompt);
            log.info("##### verifyResponseGrounding: ######################\n {}", evaluation);
            // Extract score - assumes format like "Score: 7/10" somewhere in the text
            double score = extractScoreFromEvaluation(evaluation);

            // Create verification result
            return new VerificationResult(
                    score >= 7.0, // Consider verified if score is 7 or higher
                    score,
                    evaluation,
                    findUngroundedClaims(evaluation));

        } catch (Exception e) {
            log.error("Error verifying response: {}", e.getMessage());
            // Return conservative result on error
            return new VerificationResult(
                    false,
                    0.0,
                    "Verification failed: " + e.getMessage(),
                    "Could not verify response accuracy");
        }
    }

    /**
     * Create verification prompt
     */
    private String createVerificationPrompt(String userQuery, String retrievedContext, String draftResponse) {
        return "You are a critical evaluator with expertise in fact-checking. " +
                "Your job is to analyze if a response is factually accurate and contains ONLY information " +
                "that can be directly verified from the provided context.\n\n" +
                "USER QUERY: " + userQuery + "\n\n" +
                "RETRIEVED CONTEXT:\n" + retrievedContext + "\n\n" +
                "DRAFT RESPONSE:\n" + draftResponse + "\n\n" +
                "Your evaluation tasks:\n" +
                "1. First, analyze each factual claim in the response separately and verify if it appears in the context\n" +
                "2. Score from 0-10 how factually accurate the response is (0 = completely made up, 10 = perfectly accurate)\n" +
                "3. Identify ANY statements not directly supported by the context, even minor ones\n" +
                "4. Be especially strict about dates, numbers, statistics, and specific procedures\n" +
                "5. Format your answer starting with 'Score: X/10'\n" +
                "6. Follow with detailed reasoning for each claim you checked\n" +
                "7. If any ungrounded claims exist, list them in a section titled 'Ungrounded claims:'\n\n" +
                "Remember: A perfect response should ONLY contain information from the context, nothing more.";
    }

    /**
     * Extract numerical score from evaluation text
     */
    private double extractScoreFromEvaluation(String evaluation) {
        try {
            // Look for patterns like "Score: 7/10" or "I rate this a 7 out of 10"
            String[] patterns = {
                    "Score: (\\d+(?:\\.\\d+)?)/10",
                    "(\\d+(?:\\.\\d+)?) out of 10",
                    "rating of (\\d+(?:\\.\\d+)?)/10",
                    "score of (\\d+(?:\\.\\d+)?)"
            };

            for (String patternStr : patterns) {
                Pattern pattern = Pattern.compile(patternStr);
                Matcher matcher = pattern.matcher(evaluation);
                if (matcher.find()) {
                    return Double.parseDouble(matcher.group(1));
                }
            }

            // Default fallback - extract first number found
            Pattern numPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)");
            Matcher numMatcher = numPattern.matcher(evaluation);
            if (numMatcher.find()) {
                return Double.parseDouble(numMatcher.group(1));
            }

            // If no score found, return conservative estimate
            return 5.0;
        } catch (Exception e) {
            log.error("Error extracting score: {}", e.getMessage());
            return 5.0; // Default middle score on error
        }
    }

    /**
     * Extract any ungrounded claims from evaluation
     */
    private String findUngroundedClaims(String evaluation) {
        // Look for sections that identify ungrounded claims
        String[] sections = {
                "Ungrounded claims:(.*?)(?=\\n\\n|$)",
                "Not supported by context:(.*?)(?=\\n\\n|$)",
                "Hallucinations:(.*?)(?=\\n\\n|$)",
                "Made-up information:(.*?)(?=\\n\\n|$)"
        };

        for (String sectionPattern : sections) {
            Pattern pattern = Pattern.compile(sectionPattern, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(evaluation);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }

        return "No specific ungrounded claims identified";
    }

    /**
     * Class to hold verification results
     */
    @Getter
    @Setter
    public static class VerificationResult {
        private final boolean verified;
        private final double score;
        private final String evaluation;
        private final String ungroundedClaims;

        public VerificationResult(boolean verified, double score,
                                  String evaluation, String ungroundedClaims) {
            this.verified = verified;
            this.score = score;
            this.evaluation = evaluation;
            this.ungroundedClaims = ungroundedClaims;
        }
    }
}