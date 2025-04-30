package be.fgov.bosa.chatbot.chatbot.services;

import be.fgov.bosa.chatbot.chatbot.config.AppPropertiesConfig;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Service
@AllArgsConstructor
public class RageService {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;  // 10MB limit

    private final AppPropertiesConfig appConfigProperties;
    private final EmbeddingStoreIngestor embeddingStoreIngestor;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * Save document from uploaded file
     *
     * @param file The uploaded file
     */
    public void saveDocument(MultipartFile file, String botId) {
        try {
            // Validate file
            validateFile(file);

            // Generate document ID
            String documentId = UUID.randomUUID().toString();

            // Create temp directory
            File directory = createTempDirectory();

            // Save file to temp location
            Path filePath = saveFileToTemp(file, directory);

            // Process document with standard metadata
            Map<String, String> metadata = createStandardMetadata(file, documentId, botId);

            // Process and save the document
            saveDocumentWithMetadata(new UrlResource(filePath.toUri()), metadata);

            log.info("Document saved and processed: {}, ID: {}", file.getOriginalFilename(), documentId);
        } catch (Exception e) {
            log.error("Failed to save and process document: {}", e.getMessage());
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    /**
     * Process and save document with metadata
     *
     * @param resource The document resource
     * @param metadata Metadata to associate with the document
     * @throws IOException If file cannot be read
     */
    public void saveDocumentWithMetadata(Resource resource, Map<String, String> metadata) throws IOException {
        try {
            if (!resource.exists()) {
                throw new FileNotFoundException("Resource does not exist: " + resource.getFilename());
            }

            log.info("Processing document with metadata: {}", resource.getFilename());

            // Load document content
            Document document = FileSystemDocumentLoader.loadDocument(resource.getFile().toPath());

            // Ensure required metadata
            if (metadata == null) {
                metadata = new HashMap<>();
            }

            // Add standard fields if missing
            if (!metadata.containsKey("botId")) {
                metadata.put("botId", "id");  // Required for retrieval
            }
            if (!metadata.containsKey("filename") && resource.getFilename() != null) {
                metadata.put("filename", resource.getFilename());
            }
            if (!metadata.containsKey("filetype") && resource.getFilename() != null) {
                metadata.put("filetype", getFileExtension(resource.getFilename()));
            }
            if (!metadata.containsKey("timestamp")) {
                metadata.put("timestamp", String.valueOf(System.currentTimeMillis()));
            }

            // Ingest document with metadata
            embeddingStoreIngestor.ingest(Document.from(document.text(), Metadata.from(metadata)));

           /* // Split document into chunks
            List<TextSegment> segments = splitDocument(document);
            log.info("Document split into {} segments", segments.size());

            // Store segments
            String documentId = metadata.getOrDefault("document_id", UUID.randomUUID().toString());
            storeSegments(segments, documentId);
*/
            log.info("Document with metadata successfully processed");
        } catch (Exception e) {
            log.error("Failed to process document with metadata: {}", e.getMessage());
            throw e;
        }
    }


    /**
     * Create standard metadata for documents
     *
     * @param file The uploaded file
     * @param documentId Document identifier
     * @return Map of standard metadata
     */
    private Map<String, String> createStandardMetadata(MultipartFile file, String documentId, String botId) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("botId", botId);
        metadata.put("document_id", documentId);
        metadata.put("original_filename", file.getOriginalFilename());
        metadata.put("filetype", getFileExtension(file.getOriginalFilename()));
        metadata.put("content_type", file.getContentType());
        metadata.put("size", String.valueOf(file.getSize()));
        metadata.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return metadata;
    }

    /**
     * Validate uploaded file
     *
     * @param file The file to validate
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size must not exceed " + (MAX_FILE_SIZE / (1024 * 1024)) + "MB");
        }

        String fileExtension = getFileExtension(file.getOriginalFilename());
        if (!isSupportedFileType(fileExtension)) {
            throw new IllegalArgumentException("Unsupported file type: " + fileExtension);
        }
    }

    /**
     * Create temporary directory
     *
     * @return The created directory
     */
    private File createTempDirectory() {
        File directory = new File(appConfigProperties.getDirectories().getTemp());
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new RuntimeException("Failed to create temp directory: " + directory.getAbsolutePath());
            }
        }
        return directory;
    }

    /**
     * Save file to temporary location
     *
     * @param file The file to save
     * @param directory The target directory
     * @return Path to the saved file
     * @throws IOException If file cannot be saved
     */
    private Path saveFileToTemp(MultipartFile file, File directory) throws IOException {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(directory.getAbsolutePath(), fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return filePath;
    }

    /**
     * Get file extension from filename
     *
     * @param filename The filename
     * @return The file extension
     */
    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int lastDotIndex = filename.lastIndexOf(".");
        return (lastDotIndex == -1) ? "" : filename.substring(lastDotIndex + 1).toLowerCase();
    }

    /**
     * Check if file type is supported
     *
     * @param fileExtension The file extension
     * @return True if supported
     */
    private boolean isSupportedFileType(String fileExtension) {
        return List.of(
                "txt", "pdf", "doc", "docx", "md", "html", "htm",
                "csv", "json", "xml", "rtf", "odt"
        ).contains(fileExtension);
    }

    /**
     * Clear all documents from the vector store
     */
    public void clearAllDocuments() {
        log.info("Clearing all documents from vector store");
        embeddingStore.removeAll();
        log.info("Vector store cleared");
    }

    public void clearDocuments(String botId) {
        log.info("Clearing all documents from vector store");
        embeddingStore.removeAll(metadataKey("botId").isEqualTo(botId));
        log.info("Vector store cleared");
    }



}