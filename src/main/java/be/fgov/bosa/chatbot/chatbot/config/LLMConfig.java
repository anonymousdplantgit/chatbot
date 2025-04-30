package be.fgov.bosa.chatbot.chatbot.config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.AllArgsConstructor;
import org.apache.tika.config.TikaConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@AllArgsConstructor
@Configuration
public class LLMConfig {

    final private AppPropertiesConfig appPropertiesConfig;
    final private LangChain4jPropertiesConfig langChain4jPropertiesConfig;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(langChain4jPropertiesConfig.getOllama().getChatModel().getBaseUrl())
                .modelName(langChain4jPropertiesConfig.getOllama().getChatModel().getModelName())
                .temperature(langChain4jPropertiesConfig.getOllama().getChatModel().getTemperature())
                .timeout(Duration.ofSeconds(langChain4jPropertiesConfig.getOllama().getChatModel().getTimeout()))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(langChain4jPropertiesConfig.getOllama().getEmbeddingModel().getBaseUrl())
                .modelName(langChain4jPropertiesConfig.getOllama().getEmbeddingModel().getModelName())
                .timeout(Duration.ofSeconds(langChain4jPropertiesConfig.getOllama().getEmbeddingModel().getTimeout()))
                .build();
    }

    @Bean public TikaConfig tikaConfig() {
        return TikaConfig.getDefaultConfig();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        // Creates a memory provider that:
        // - Maintains separate chat history for each chat ID
        // - Keeps last 10 messages in memory for context
        // - Helps maintain conversation coherence
        return chatId -> MessageWindowChatMemory.withMaxMessages(10);
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // Configures PostgreSQL with pgvector extension to:
        // - Store document embeddings as vectors
        // - Enable similarity search
        // - Auto-create required tables
        // - Match embedding dimensions with model

        return PgVectorEmbeddingStore.builder()
                .host(appPropertiesConfig.getDatasource().getHost())
                .port(appPropertiesConfig.getDatasource().getPort())
                .database(appPropertiesConfig.getDatasource().getDatabase())
                .user(appPropertiesConfig.getDatasource().getUsername())
                .password(appPropertiesConfig.getDatasource().getPassword())
                .table(appPropertiesConfig.getDatasource().getEmbeddingsTable())
                .dimension(embeddingModel().dimension())  // Automatically matches model's embedding size
                .createTable(true)  // Creates table if it doesn't exist
                .build();


    }

    @Bean
    public EmbeddingStoreIngestor embeddingStoreIngestor(EmbeddingModel embeddingModel) {
        // Creates a document ingestor that:
        // - Splits documents into chunks of 1000 tokens
        // - Uses 100 tokens overlap to maintain context between chunks
        // - Uses the provided embedding model to convert text to vectors
        // - Stores the vectors in the provided embedding store
        return EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter())
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore())
                .build();
    }

    @Bean
    public ContentRetriever contentRetriever( EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore())
                .embeddingModel(embeddingModel)
                .maxResults(appPropertiesConfig.getRag().getRetrieval().getMaxResults())
                .minScore(appPropertiesConfig.getRag().getRetrieval().getMinScore())
                .build();
    }

    @Bean
    public DocumentSplitter documentSplitter() {
        return DocumentSplitters.recursive(
                appPropertiesConfig.getRag().getIndexing().getChunkSize(),
                appPropertiesConfig.getRag().getIndexing().getChunkOverlap());
    }

    @Bean
    public RetrievalAugmentor retrievalAugmentor() {
        QueryTransformer queryTransformer = new CompressingQueryTransformer(chatLanguageModel());
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever(embeddingModel()))
                .queryTransformer(queryTransformer)
                .build();
    }

}
