package be.fgov.bosa.chatbot.chatbot.resources;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {
    private String response;
    private String sessionId;
    private double  confidence;
    private double    retrievalConfidence;
    private boolean lowConfidence;
    private String   fallbackMessage;
}
