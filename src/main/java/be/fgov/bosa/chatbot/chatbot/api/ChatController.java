package be.fgov.bosa.chatbot.chatbot.api;

import be.fgov.bosa.chatbot.chatbot.requests.ChatRequest;
import be.fgov.bosa.chatbot.chatbot.requests.UploadRequest;
import be.fgov.bosa.chatbot.chatbot.resources.ChatResponse;
import be.fgov.bosa.chatbot.chatbot.resources.UploadResponse;
import be.fgov.bosa.chatbot.chatbot.services.ChatBotService;
import be.fgov.bosa.chatbot.chatbot.services.RageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping(value="/api/public/chat", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    private final RageService rageService;
    private final ChatBotService chatBotService;


    @PostMapping("/v2")
    public ChatResponse chatWithAiBotV2(@RequestBody ChatRequest request) throws IOException {
        log.info("info request - > You: {}",request.getMessage());
        ChatResponse response  = chatBotService.chat(request);
        log.info("info response <-- bot: {}", response.getResponse());
        return response;
    }



    @PostMapping(value = "/upload-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadDocument(
            @RequestPart UploadRequest request,
            @RequestPart("file") MultipartFile file) {
        try {
            rageService.saveDocument(file, request.getBotId());
            return ResponseEntity.ok(new UploadResponse("Document processed successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UploadResponse("Error processing document: " + e.getMessage()));
        }
    }

    @DeleteMapping(value = "/{botId}")
    public ResponseEntity<Void> clearBotKnowledgeBase(
            @PathVariable String botId) {
        try {
            rageService.clearDocuments(botId);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);        }
    }




}
