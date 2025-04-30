package be.fgov.bosa.chatbot.chatbot.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for extracting text content from various document formats
 */
@Slf4j
@Component
public class DocumentExtractor {

    private final Tika tika;
    private final Parser parser;

    public DocumentExtractor() {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
    }

    /**
     * Extract text content from file
     *
     * @param file The uploaded file
     * @return Extracted text content
     * @throws IOException If file cannot be read
     */
    public String extractText(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            // Create handler with unlimited size to avoid truncation
            ContentHandler handler = new BodyContentHandler(-1);
            org.apache.tika.metadata.Metadata tikaMetadata = new org.apache.tika.metadata.Metadata();

            // Set file metadata - using proper Tika constants
            tikaMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());
            tikaMetadata.set(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE, file.getContentType());

            // Parse content
            parser.parse(inputStream, handler, tikaMetadata, new ParseContext());

            // Get extracted text
            String content = handler.toString();
            log.debug("Extracted {} characters from {}", content.length(), file.getOriginalFilename());

            return content;
        } catch (TikaException | SAXException e) {
            log.error("Error extracting text from {}: {}", file.getOriginalFilename(), e.getMessage());
            throw new IOException("Failed to extract text: " + e.getMessage(), e);
        }
    }

    /**
     * Extract text content and metadata from file
     *
     * @param file The uploaded file
     * @return Map containing content and metadata
     * @throws IOException If file cannot be read
     */
    public Map<String, Object> extractContentAndMetadata(MultipartFile file) throws IOException {
        Map<String, Object> result = new HashMap<>();

        try (InputStream inputStream = file.getInputStream()) {
            // Create handler with unlimited size to avoid truncation
            ContentHandler handler = new BodyContentHandler(-1);
            org.apache.tika.metadata.Metadata tikaMetadata = new org.apache.tika.metadata.Metadata();

            // Set file metadata - using proper Tika constants
            tikaMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());

            // Parse content
            parser.parse(inputStream, handler, tikaMetadata, new ParseContext());

            // Get content
            String content = handler.toString();
            result.put("content", content);

            // Get metadata
            Map<String, String> metadataMap = new HashMap<>();
            for (String name : tikaMetadata.names()) {
                metadataMap.put(name, tikaMetadata.get(name));
            }
            result.put("metadata", metadataMap);

            log.debug("Extracted content ({} chars) and {} metadata fields from {}",
                    content.length(), metadataMap.size(), file.getOriginalFilename());

            return result;
        } catch (TikaException | SAXException e) {
            log.error("Error extracting content from {}: {}", file.getOriginalFilename(), e.getMessage());
            throw new IOException("Failed to extract content: " + e.getMessage(), e);
        }
    }

    /**
     * Detect MIME type of file
     *
     * @param file The uploaded file
     * @return Detected MIME type
     * @throws IOException If file cannot be read
     */
    public String detectMimeType(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        }
    }

    /**
     * Clean and normalize extracted text
     *
     * @param text Raw extracted text
     * @return Cleaned text
     */
    public String cleanText(String text) {
        if (text == null) {
            return "";
        }

        // Replace multiple whitespace with single space
        String cleaned = text.replaceAll("\\s+", " ");

        // Remove control characters
        cleaned = cleaned.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");

        // Normalize line breaks
        cleaned = cleaned.replaceAll("\\r\\n|\\r", "\n");

        // Remove excessive line breaks
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");

        return cleaned.trim();
    }
}