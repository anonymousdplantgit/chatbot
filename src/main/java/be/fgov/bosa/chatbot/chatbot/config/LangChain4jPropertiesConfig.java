package be.fgov.bosa.chatbot.chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "lang-chain4j")
public class LangChain4jPropertiesConfig {
    private final Ollama ollama = new Ollama();

    @Getter
    @Setter
    public static class Ollama {
        private ChatModel chatModel = new ChatModel();
        private EmbeddingModel embeddingModel = new EmbeddingModel();

        @Getter
        @Setter
        public static class ChatModel {
            private String baseUrl;
            private String modelName;
            private double temperature;
            private long timeout;
        }

        @Getter
        @Setter
        public static class EmbeddingModel {
            private String baseUrl;
            private String modelName;
            private long timeout;
        }
    }
}
