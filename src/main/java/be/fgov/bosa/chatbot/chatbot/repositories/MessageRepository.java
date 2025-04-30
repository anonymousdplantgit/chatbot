package be.fgov.bosa.chatbot.chatbot.repositories;

import be.fgov.bosa.chatbot.chatbot.models.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByConversationIdOrderByTimestampAsc(UUID conversationId);
}
