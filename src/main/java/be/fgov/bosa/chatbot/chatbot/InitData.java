package be.fgov.bosa.chatbot.chatbot;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.AllArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class InitData implements ApplicationRunner {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStoreIngestor embeddingStoreIngestor;
    private final EmbeddingStore<TextSegment> embeddingStore;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        TextSegment segment1 = TextSegment.from("I like football.", Metadata.metadata("userId", "1"));
        Embedding embedding1 = embeddingModel.embed(segment1).content();
        embeddingStore.add(embedding1, segment1);
    }
}
