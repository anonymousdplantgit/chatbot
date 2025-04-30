package be.fgov.bosa.chatbot.chatbot.resources;

import be.fgov.bosa.chatbot.chatbot.models.Resource;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class AllResources {
    ChatbotResource chatbot;
    TrainingDataResource trainingData;
    Resource resource;
    ConversationResource conversation;
    MessageResource message;
    OrganizationResource organization;

}
