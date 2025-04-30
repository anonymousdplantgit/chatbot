package be.fgov.bosa.chatbot.chatbot.resources;

import be.fgov.bosa.chatbot.chatbot.enums.ChatbotStatusEnum;
import be.fgov.bosa.chatbot.chatbot.models.Organization;
import lombok.*;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ChatbotResource {
    private UUID id;
    
    private String name;
    
    private String description;
    
    private ChatbotStatusEnum status;
    
    private Organization organization;
    
}

