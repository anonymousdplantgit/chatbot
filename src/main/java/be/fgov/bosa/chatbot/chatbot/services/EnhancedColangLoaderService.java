package be.fgov.bosa.chatbot.chatbot.services;

import be.fgov.bosa.chatbot.chatbot.config.AppPropertiesConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@AllArgsConstructor
@Getter
@Setter
public class EnhancedColangLoaderService {
    // Cache for loaded colang files
    private final Map<String, String> colangCache = new HashMap<>();
    private final ResourceLoader resourceLoader;
    final private AppPropertiesConfig appConfigProperties;

    /**
     * Loads a Colang file from resources or cache
     *
     * @return Colang content as string
     * @throws IOException If file cannot be read
     */
    public String loadColangAsSystemPrompt() throws IOException {
        // Check cache first
        String location = appConfigProperties.getChatbot().getSystemPrompt(); // "classpath:system-prompt.md"
        if (colangCache.containsKey(location)) {
            log.debug("Returning cached colang file: {}", location);
            return colangCache.get(location);
        }

        // Load from resources
        try {
            Resource resource = resourceLoader.getResource(location);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Cache the content
            colangCache.put(location, content);

            return content;
        } catch (IOException e) {
            log.error("Failed to load Colang file: {}", location, e);
            throw e;
        }
    }

    /**
     * Creates a formatted system prompt from a colang file
     *
     * @return Formatted system prompt
     * @throws IOException If file cannot be read
     */
    public String createFormattedSystemPrompt() throws IOException {
        String colangContent = loadColangAsSystemPrompt();

        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("You are a ministry information assistant with the following strict guardrails:\n\n");
        systemPrompt.append("```colang\n");
        systemPrompt.append(colangContent);
        systemPrompt.append("\n```\n\n");

        systemPrompt.append("Core operating principles:\n");
        systemPrompt.append("1. FACTUAL ACCURACY: Only provide information explicitly stated in the knowledge base\n");
        systemPrompt.append("2. REFUSAL CLARITY: When declining to answer, explain policy reasons without elaboration\n");
        systemPrompt.append("3. INFORMATION BOUNDARY: Never speculate or infer beyond provided context\n");
        systemPrompt.append("4. TRANSPARENCY: Clearly indicate uncertainty when information is incomplete\n");
        systemPrompt.append("5. ATTRIBUTION: Reference specific segments when possible\n");
        systemPrompt.append("6. LANGUAGE ADAPTATION: Always respond with the same language that the user used to ask the question\n\n");

        systemPrompt.append("Response formulation rules:\n");
        systemPrompt.append("- Always respond with the language that the user used to ask the question\n");
        systemPrompt.append("- Structure responses with verified facts first\n");
        systemPrompt.append("- For incomplete information, state what is known before explaining limitations\n");
        systemPrompt.append("- Never fabricate details to make answers seem more complete\n");
        systemPrompt.append("- Use precise language that matches the confidence level of the information\n");


        return systemPrompt.toString();
    }

    /**
     * Reloads all cached colang files
     */
    public void refreshCache() {
        log.info("Refreshing colang file cache");
        Map<String, String> newCache = new HashMap<>();
        String location = appConfigProperties.getChatbot().getSystemPrompt(); // "classpath:system-prompt.md"
        for (String fileName : colangCache.keySet()) {
            try {
                Resource resource = resourceLoader.getResource(location);
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                newCache.put(fileName, content);
            } catch (IOException e) {
                log.error("Failed to refresh colang file: {}", fileName, e);
                // Keep the old version in cache
                newCache.put(fileName, colangCache.get(fileName));
            }
        }

        // Replace the cache atomically
        synchronized (colangCache) {
            colangCache.clear();
            colangCache.putAll(newCache);
        }
    }
}
