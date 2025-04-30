package be.fgov.bosa.chatbot.chatbot.requests;

import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ChatRequest {
    private String message;
    private String conversationId;
    private String botId;
}