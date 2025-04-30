package be.fgov.bosa.chatbot.chatbot.repositories;

import be.fgov.bosa.chatbot.chatbot.models.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
}


