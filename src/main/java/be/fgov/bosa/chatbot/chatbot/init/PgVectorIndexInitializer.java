package be.fgov.bosa.chatbot.chatbot.init;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PgVectorIndexInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final JdbcTemplate jdbcTemplate;

    public PgVectorIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("=======> onApplicationEvent PgVectorIndexInitializer executing .... <=========");

        try {
            // Check if index exists first
            boolean indexExists = checkIfIndexExists();
            log.info("=======> onApplicationEvent checkIfIndexExists {}} <=========",indexExists);

            if (!indexExists) {
                // Create the index if it doesn't exist
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS embeddings_idx " +
                                "ON embeddings USING hnsw (embedding vector_cosine_ops)"
                );
                log.info("=======> HNSW index created for embeddings column <=========");
            }else {
                log.info("=======> !! HNSW index already exists for embeddings column <=========");
            }
        } catch (Exception e) {
            System.err.println("Error creating HNSW index: " + e.getMessage());
            // Don't throw exception as this shouldn't stop application startup
        }
    }

    private boolean checkIfIndexExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'embeddings_idx'",
                Integer.class
        );
        return count != null && count > 0;
    }
}
