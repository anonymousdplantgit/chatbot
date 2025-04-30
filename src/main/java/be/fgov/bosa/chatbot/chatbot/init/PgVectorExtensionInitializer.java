package be.fgov.bosa.chatbot.chatbot.init;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Component
public class PgVectorExtensionInitializer implements InitializingBean {

    private final DataSource dataSource;

    public PgVectorExtensionInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Create pgvector extension
            String query = "CREATE EXTENSION IF NOT EXISTS vector";
            log.info("=======> Executing query: {} <=========",query);
            stmt.execute(query);
            log.info("=======> Successfully created pgvector extension <=========");
        } catch (SQLException e) {
            System.err.println("Failed to create pgvector extension: " + e.getMessage());
            throw e; // Rethrow to prevent application startup if extension can't be created
        }
    }
}
