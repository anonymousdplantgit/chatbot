package be.fgov.bosa.chatbot.chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppPropertiesConfig {
    private final Datasource datasource = new Datasource();
    private final Directories directories = new Directories();
    private final Chatbot chatbot = new Chatbot();
    private final Rag rag = new Rag();

    @Getter
    @Setter
    public static class Chatbot {
        private String systemPrompt;
        private double confidenceThreshold = 0.65;
        private int memoryTokenLimit = 4096;
        private int maxResponseTokens = 2048;
        private boolean verifyResponses = true;
        private boolean safetyFilter = true;
    }

    @Getter
    @Setter
    public static class Directories {
        private String temp;
        private String logs;
        private String uploads;
    }

    @Getter
    @Setter
    public static class Datasource {
        private String database;
        private String host;
        private int port;
        private String username;
        private String password;
        private String embeddingsTable;
        private int connectionPoolSize = 5;
        private int maxConnections = 20;
        private int connectionTimeout = 5000;
    }

    @Getter
    @Setter
    public static class Rag {
        private final Retrieval retrieval = new Retrieval();
        private final Indexing indexing = new Indexing();

        @Getter
        @Setter
        public static class Retrieval {
            private int maxResults = 5;
            private double minScore = 0.7;
            private boolean rerankingEnabled = true;
        }

        @Getter
        @Setter
        public static class Indexing {
            private int chunkSize = 1000;
            private int chunkOverlap = 300;
            private String defaultLanguage = "en";
        }
    }
}