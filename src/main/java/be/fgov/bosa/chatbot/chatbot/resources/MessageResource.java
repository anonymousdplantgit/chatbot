package be.fgov.bosa.chatbot.chatbot.resources;

import be.fgov.bosa.chatbot.chatbot.enums.MessageTypeEnum;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class MessageResource {
    private UUID id;
    
    private ConversationResource conversation;
    
    private String content;
    
    private OffsetDateTime timestamp;
    
    private MessageTypeEnum type;

}

