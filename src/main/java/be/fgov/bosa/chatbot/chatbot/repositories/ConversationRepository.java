package be.fgov.bosa.chatbot.chatbot.repositories;

import be.fgov.bosa.chatbot.chatbot.models.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    List<Conversation> findByChatbotId(UUID chatbotId);
    Optional<Conversation> findByConversationId(String conversationId);
}
