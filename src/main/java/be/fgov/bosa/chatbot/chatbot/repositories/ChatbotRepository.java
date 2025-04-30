package be.fgov.bosa.chatbot.chatbot.repositories;

import be.fgov.bosa.chatbot.chatbot.models.Chatbot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatbotRepository extends JpaRepository<Chatbot, UUID> {
    List<Chatbot> findByOrganizationId(UUID organizationId);
}
